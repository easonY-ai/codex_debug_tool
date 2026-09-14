package dev.tracelens.api;

import dev.tracelens.ingestion.OtelIngestionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
public class OtelController {
    private final OtelIngestionService service;
    public OtelController(OtelIngestionService service) { this.service = service; }

    @PostMapping(value = "/v1/{signal:logs|traces|metrics}", consumes = "application/json")
    public Map<String, Object> receive(@PathVariable String signal, @RequestBody byte[] body) {
        if (body.length > 4 * 1024 * 1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "OTLP_REQUEST_TOO_LARGE");
        try { service.accept(signal, body); return Map.of("partialSuccess", Map.of()); }
        catch (IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_OTLP_JSON"); }
    }

    @GetMapping("/api/ingestion/otel/status")
    public Map<String, Object> status() { return service.status(); }
}
