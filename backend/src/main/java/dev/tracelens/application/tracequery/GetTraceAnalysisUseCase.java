package dev.tracelens.application.tracequery;

import dev.tracelens.application.tracequery.TraceAnalysisResult.TraceAggregates;
import dev.tracelens.application.tracequery.TraceAnalysisResult.TraceCoverage;
import dev.tracelens.application.tracequery.TraceAnalysisResult.TraceDiagnosis;
import dev.tracelens.domain.operationaldiagnostics.AuditedBusinessOperations;
import dev.tracelens.domain.tracequery.TraceAnalysisRepository;
import dev.tracelens.domain.tracequery.TraceAnalysisRepository.PerformanceSpanEvidence;
import dev.tracelens.domain.tracequery.TraceAnalysisRepository.TraceTool;
import dev.tracelens.domain.tracequery.TraceAnalysisRepository.TraceTurn;
import dev.tracelens.domain.transcriptcontent.JsonlSupplementRepository;
import dev.tracelens.domain.transcriptcontent.TranscriptSupplementEvidence;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Application use case that assembles one Trace without exposing Mapper rows to HTTP. */
@Service
@AuditedBusinessOperations
public class GetTraceAnalysisUseCase {
    private final TraceAnalysisRepository traceAnalysisRepository;
    private final JsonlSupplementRepository jsonlSupplementRepository;

    public GetTraceAnalysisUseCase(
            TraceAnalysisRepository traceAnalysisRepository,
            JsonlSupplementRepository jsonlSupplementRepository) {
        this.traceAnalysisRepository = traceAnalysisRepository;
        this.jsonlSupplementRepository = jsonlSupplementRepository;
    }

    public TraceAnalysisResult get(String turnId) {
        TraceTurn turn = traceAnalysisRepository.findTurn(turnId);
        if (turn == null) {
            return null;
        }
        List<TraceTool> tools = traceAnalysisRepository.findTools(turnId);
        var hookEvents = traceAnalysisRepository.findHookEvents(turnId);
        List<PerformanceSpanEvidence> performance =
                traceAnalysisRepository.findPerformanceSpans(turnId);
        var alignments = traceAnalysisRepository.findAlignments(turnId);
        List<TranscriptSupplementEvidence> supplements =
                jsonlSupplementRepository.findForTurn(turnId);

        long startedAt = number(turn.startedAt());
        long endedAt = number(turn.endedAt());
        long totalMs = Math.max(0, endedAt - startedAt);
        long hookToolMs = coveredMs(
                tools.stream()
                        .filter(tool -> tool.durationValid() == 1)
                        .map(tool -> new long[]{
                                number(tool.preObservedAt()),
                                number(tool.postObservedAt())})
                        .toList(),
                startedAt,
                endedAt);
        long otelToolMs = coveredMs(
                performance.stream()
                        .filter(span -> span.callId() != null)
                        .map(span -> new long[]{
                                number(span.eventTime()),
                                number(span.eventTime()) + number(span.durationMs())})
                        .toList(),
                startedAt,
                endedAt);
        long toolMs = otelToolMs > 0 ? otelToolMs : hookToolMs;

        double hookCoverage = turn.startedAt() != null && turn.endedAt() != null ? 1 : 0.5;
        double transcriptCoverage = "MATCHED".equals(turn.sessionCheckStatus()) ? 1 : 0;
        double contentCoverage = supplements.isEmpty() ? 0 : 1;
        double otelCoverage = turn.toolCount() == 0
                ? performance.isEmpty() ? 0 : 1
                : (double) turn.otelToolCount() / turn.toolCount();
        int completeness = (int) Math.round((
                hookCoverage + transcriptCoverage + contentCoverage + otelCoverage) * 25);

        List<TraceDiagnosis> diagnoses = diagnoses(toolMs, otelToolMs, performance.isEmpty());
        TraceAggregates aggregates = new TraceAggregates(
                totalMs,
                toolMs,
                0,
                0,
                0,
                Math.max(0, totalMs - toolMs),
                performance.isEmpty() ? "MISSING" : "PARTIAL",
                supplements.isEmpty() ? "MISSING" : "AVAILABLE",
                completeness,
                new TraceCoverage(
                        hookCoverage,
                        transcriptCoverage,
                        contentCoverage,
                        otelCoverage));
        return new TraceAnalysisResult(
                turn,
                hookEvents,
                tools,
                supplements,
                performance,
                alignments,
                diagnoses,
                aggregates);
    }

    private static List<TraceDiagnosis> diagnoses(
            long toolMs,
            long otelToolMs,
            boolean performanceMissing) {
        List<TraceDiagnosis> diagnoses = new ArrayList<>();
        if (toolMs >= 10_000) {
            diagnoses.add(new TraceDiagnosis(
                    "slow-tool",
                    "主要瓶颈",
                    "工具执行耗时过长",
                    "工具阶段超过确定性规则阈值 10 秒",
                    toolMs,
                    otelToolMs > 0 ? "高" : "中",
                    otelToolMs > 0 ? "OTel" : "Hook 估算"));
        }
        if (performanceMissing) {
            diagnoses.add(new TraceDiagnosis(
                    "missing-otel",
                    "提示",
                    "缺少性能数据",
                    "无法计算 API、TTFT 与精确工具耗时",
                    0,
                    "高",
                    "完整度检查"));
        }
        return List.copyOf(diagnoses);
    }

    private static long number(Long value) {
        return value == null ? 0 : value;
    }

    private static long coveredMs(List<long[]> intervals, long lower, long upper) {
        if (lower <= 0 || upper <= lower) {
            return 0;
        }
        List<long[]> clipped = intervals.stream()
                .map(interval -> new long[]{
                        Math.max(lower, interval[0]),
                        Math.min(upper, interval[1])})
                .filter(interval -> interval[1] >= interval[0])
                .sorted(Comparator.comparingLong(interval -> interval[0]))
                .toList();
        long covered = 0;
        long start = -1;
        long end = -1;
        for (long[] interval : clipped) {
            if (start < 0) {
                start = interval[0];
                end = interval[1];
            } else if (interval[0] <= end) {
                end = Math.max(end, interval[1]);
            } else {
                covered += end - start;
                start = interval[0];
                end = interval[1];
            }
        }
        return start < 0 ? 0 : covered + end - start;
    }
}
