package dev.tracelens.api;

import dev.tracelens.domain.operationaldiagnostics.AuditedBusinessOperations;
import dev.tracelens.ingestion.JsonlScanner;
import dev.tracelens.persistence.IngestionMapper;
import dev.tracelens.persistence.RawRecord;
import dev.tracelens.persistence.SourceFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/ingestion")
@AuditedBusinessOperations
public class IngestionController {
    public record Status(String status, long checkedAt, JsonlScanner.ScannerState scanner,
                         List<SourceFile> files, long rawRecords, Map<String, String> capabilities) { }
    public record RecordPage(List<RawRecord> items, Long nextCursor, boolean hasMore) { }
    private final JsonlScanner scanner;
    private final IngestionMapper mapper;

    public IngestionController(JsonlScanner scanner, IngestionMapper mapper) {
        this.scanner = scanner;
        this.mapper = mapper;
    }

    @GetMapping("/transcripts/status")
    public Map<String, Object> transcriptStatus() {
        var items = mapper.transcriptBindings();
        return Map.of("items", items, "total", items.size());
    }

    @GetMapping("/status")
    public Status status() {
        var state = scanner.state();
        return new Status(state.enabled() ? "PARTIAL" : "DISABLED", System.currentTimeMillis(), state,
                mapper.sources(), mapper.countRecords(), Map.of("jsonl", state.enabled() ? "ENABLED" : "DISABLED",
                "otlp", "ENABLED", "normalization", "ENABLED", "correlation", "PARTIAL",
                "analysis", "ENABLED", "sse", "ENABLED"));
    }

    @PostMapping("/rescan")
    public JsonlScanner.ScanResult rescan() {
        if (!scanner.state().enabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "INGESTION_DISABLED");
        return scanner.scan();
    }

    @GetMapping("/records")
    public RecordPage records(@RequestParam(defaultValue = "0") long afterId,
                              @RequestParam(defaultValue = "50") int limit,
                              @RequestParam(required = false) Long sourceId,
                              @RequestParam(required = false) String parseStatus) {
        if (afterId < 0 || limit < 1 || limit > 200 || (sourceId != null && sourceId < 1)
                || (parseStatus != null && !Set.of("VALID_JSON", "INVALID_JSON", "INVALID_UTF8").contains(parseStatus))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_QUERY");
        }
        List<RawRecord> rows = mapper.records(afterId, limit + 1, sourceId, parseStatus);
        boolean more = rows.size() > limit;
        List<RawRecord> items = more ? rows.subList(0, limit) : rows;
        return new RecordPage(items, more ? items.get(items.size() - 1).id() : null, more);
    }
}
