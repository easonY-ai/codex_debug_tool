package dev.tracelens.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tracelens.ingestion.HookIngestionService;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionController {
    public record Status(String status, long checkedAt, JsonlScanner.ScannerState scanner,
                         List<SourceFile> files, long rawRecords, Map<String, String> capabilities) { }
    public record RecordPage(List<RawRecord> items, Long nextCursor, boolean hasMore) { }
    private final JsonlScanner scanner;
    private final IngestionMapper mapper;
    private final HookIngestionService hooks;
    private final ObjectMapper json;

    public IngestionController(JsonlScanner scanner, IngestionMapper mapper, HookIngestionService hooks, ObjectMapper json) {
        this.scanner = scanner;
        this.mapper = mapper;
        this.hooks = hooks;
        this.json = json;
    }

    @PostMapping(value = "/hooks", consumes = "application/json", produces = "application/json")
    public org.springframework.http.ResponseEntity<HookIngestionService.Result> hook(
            @RequestBody byte[] body,
            @RequestHeader("X-Trace-Lens-Forwarder-Version") String headerVersion) {
        if (body.length > 1024 * 1024) {
            hooks.recordClientError();
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "HOOK_REQUEST_TOO_LARGE");
        }
        try {
            JsonNode envelope = json.readTree(body);
            if (envelope == null || !envelope.isObject() || envelope.size() != 5
                    || envelope.path("schemaVersion").asInt(-1) != 1
                    || !envelope.path("deliveryId").isTextual()
                    || !envelope.path("observedAt").canConvertToLong()
                    || envelope.path("observedAt").asLong() < 0
                    || !envelope.path("forwarderVersion").isTextual()
                    || envelope.path("forwarderVersion").asText().isBlank()
                    || !headerVersion.equals(envelope.path("forwarderVersion").asText())
                    || !envelope.path("rawEvent").isObject()) {
                throw new IllegalArgumentException("invalid envelope");
            }
            String deliveryId = envelope.path("deliveryId").asText();
            UUID.fromString(deliveryId);
            HookIngestionService.Result result = hooks.accept(deliveryId, envelope.path("observedAt").asLong(),
                    headerVersion, envelope.path("rawEvent"));
            HttpStatus status = "ACCEPTED".equals(result.status()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
            return org.springframework.http.ResponseEntity.status(status).body(result);
        } catch (IllegalArgumentException | java.io.IOException error) {
            hooks.recordClientError();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_HOOK_ENVELOPE");
        } catch (org.springframework.dao.DataAccessException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "HOOK_STORAGE_UNAVAILABLE");
        }
    }

    @GetMapping("/hooks/status")
    public HookIngestionService.Status hookStatus() { return hooks.status(); }

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
