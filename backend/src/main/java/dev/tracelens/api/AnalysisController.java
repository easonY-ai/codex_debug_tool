package dev.tracelens.api;

import dev.tracelens.domain.operationaldiagnostics.AuditedBusinessOperations;
import dev.tracelens.persistence.IngestionMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@AuditedBusinessOperations
public class AnalysisController {
    private final IngestionMapper mapper;
    private final EventStreamService streams;

    public AnalysisController(IngestionMapper mapper,EventStreamService streams) { this.mapper = mapper; this.streams=streams; }

    @GetMapping("/sessions")
    public Map<String, Object> sessions(@RequestParam(defaultValue = "") String query,
                                        @RequestParam(defaultValue = "50") int limit,
                                        @RequestParam(defaultValue = "0") int offset) {
        if (limit < 1 || limit > 200 || offset < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_QUERY");
        return Map.of("items", mapper.sessionTurns(query, limit, offset), "total", mapper.countSessionTurns(query),
                "limit", limit, "offset", offset);
    }

    @GetMapping("/sessions/{turnId}/analysis")
    public ResponseEntity<Map<String, Object>> analysis(@PathVariable String turnId) {
        Map<String, Object> turn = mapper.turnById(turnId);
        if (turn == null) return ResponseEntity.notFound().build();
        List<Map<String, Object>> tools = mapper.toolsForTurn(turnId);
        List<Map<String, Object>> events = mapper.hookEventsForTurn(turnId);
        long startedAt = number(turn.get("started_at"));
        long endedAt = number(turn.get("ended_at"));
        long total = endedAt - startedAt;
        var performance=mapper.performanceForTurn(turnId); var alignments=mapper.alignmentsForTurn(turnId); var supplements=mapper.supplementsForTurn(turnId);
        long hookToolMs = coveredMs(tools.stream().filter(it -> number(it.get("duration_valid")) == 1)
                .map(it -> new long[]{number(it.get("pre_observed_at")), number(it.get("post_observed_at"))}).toList(), startedAt, endedAt);
        long otelToolMs=coveredMs(performance.stream().filter(it->it.get("call_id")!=null)
                .map(it -> new long[]{number(it.get("event_time")), number(it.get("event_time")) + number(it.get("duration_ms"))}).toList(), startedAt, endedAt);
        long toolMs=otelToolMs>0?otelToolMs:hookToolMs;
        double hookCoverage=turn.get("started_at")!=null&&turn.get("ended_at")!=null?1:0.5;
        double transcriptCoverage="MATCHED".equals(turn.get("session_check_status"))?1:0;
        double contentCoverage=supplements.isEmpty()?0:1; long toolCount=number(turn.get("tool_count"));
        double otelCoverage=toolCount==0?(performance.isEmpty()?0:1):(double)number(turn.get("otel_tool_count"))/toolCount;
        int completeness=(int)Math.round((hookCoverage+transcriptCoverage+contentCoverage+otelCoverage)*25);
        List<Map<String,Object>> diagnoses=new java.util.ArrayList<>();
        if(toolMs>=10_000)diagnoses.add(Map.of("id","slow-tool","severity","主要瓶颈","title","工具执行耗时过长","detail","工具阶段超过确定性规则阈值 10 秒","impactMs",toolMs,"confidence",otelToolMs>0?"高":"中","source",otelToolMs>0?"OTel":"Hook 估算"));
        if(performance.isEmpty())diagnoses.add(Map.of("id","missing-otel","severity","提示","title","缺少性能数据","detail","无法计算 API、TTFT 与精确工具耗时","impactMs",0,"confidence","高","source","完整度检查"));
        return ResponseEntity.ok(Map.of(
                "session", turn, "agentEvents", events, "toolCalls", tools, "jsonlSupplements", supplements,
                "performanceSpans", performance, "alignments", alignments, "diagnoses", diagnoses,
                "aggregates", Map.of("totalMs", Math.max(0, total), "toolMs", toolMs,
                        "modelRequestMs", 0, "approvalMs", 0, "localMs", 0,
                        "unattributedMs", Math.max(0, total - toolMs), "otelStatus", performance.isEmpty()?"MISSING":"PARTIAL",
                        "contentStatus", supplements.isEmpty()?"MISSING":"AVAILABLE","completeness",completeness,
                        "coverage",Map.of("hook",hookCoverage,"transcript",transcriptCoverage,"content",contentCoverage,"otel",otelCoverage))));
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        List<Map<String, Object>> items = mapper.sessionTurns("", 200, 0);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("turns", items.size()); result.put("items", items); result.put("ttftMs", null);
        result.put("performanceStatus", "OTEL_MISSING");
        return result;
    }

    @GetMapping("/events")
    public SseEmitter events() {
        return streams.subscribe();
    }

    private static long number(Object value) { return value instanceof Number number ? number.longValue() : 0; }

    private static long coveredMs(List<long[]> intervals, long lower, long upper) {
        if (lower <= 0 || upper <= lower) return 0;
        List<long[]> clipped = intervals.stream().map(it -> new long[]{Math.max(lower, it[0]), Math.min(upper, it[1])})
                .filter(it -> it[1] >= it[0]).sorted(java.util.Comparator.comparingLong(it -> it[0])).toList();
        long covered = 0, start = -1, end = -1;
        for (long[] interval : clipped) {
            if (start < 0) { start = interval[0]; end = interval[1]; }
            else if (interval[0] <= end) end = Math.max(end, interval[1]);
            else { covered += end - start; start = interval[0]; end = interval[1]; }
        }
        return start < 0 ? 0 : covered + end - start;
    }
}
