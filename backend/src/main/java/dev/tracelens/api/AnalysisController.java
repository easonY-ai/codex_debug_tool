package dev.tracelens.api;

import dev.tracelens.domain.operationaldiagnostics.AuditedBusinessOperations;
import dev.tracelens.persistence.IngestionMapper;
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

    public AnalysisController(IngestionMapper mapper, EventStreamService streams) {
        this.mapper = mapper;
        this.streams = streams;
    }

    @GetMapping("/sessions")
    public Map<String, Object> sessions(@RequestParam(defaultValue = "") String query,
                                        @RequestParam(defaultValue = "50") int limit,
                                        @RequestParam(defaultValue = "0") int offset) {
        if (limit < 1 || limit > 200 || offset < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_QUERY");
        return Map.of("items", mapper.sessionTurns(query, limit, offset), "total", mapper.countSessionTurns(query),
                "limit", limit, "offset", offset);
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

}
