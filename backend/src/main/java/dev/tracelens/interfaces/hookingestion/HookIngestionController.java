package dev.tracelens.interfaces.hookingestion;

import dev.tracelens.application.hookingestion.AcceptHookEventUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** HTTP adapter for typed Hook envelopes; all ingestion decisions belong to the application use case. */
@RestController
@RequestMapping("/api/ingestion")
public class HookIngestionController {
    static final int MAX_HOOK_REQUEST_BYTES = 1024 * 1024;
    private final AcceptHookEventUseCase useCase;

    public HookIngestionController(AcceptHookEventUseCase useCase) { this.useCase = useCase; }

    @PostMapping(value = "/hooks", consumes = "application/json", produces = "application/json")
    public ResponseEntity<HookAcceptanceResponse> hook(@RequestBody HookEnvelopeRequest request,
                                                         @RequestHeader("X-Trace-Lens-Forwarder-Version") String headerVersion) {
        try {
            validate(request, headerVersion);
            var result = useCase.accept(request.deliveryId(), request.observedAt(), request.forwarderVersion(), request.rawEvent().rawJson());
            HttpStatus status = "ACCEPTED".equals(result.status()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
            return ResponseEntity.status(status).body(new HookAcceptanceResponse(result.deliveryId(), result.status()));
        } catch (IllegalArgumentException error) {
            useCase.recordClientError();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_HOOK_ENVELOPE");
        }
    }

    @GetMapping("/hooks/status")
    public AcceptHookEventUseCase.Status status() { return useCase.status(); }

    private static void validate(HookEnvelopeRequest request, String headerVersion) {
        if (request == null || request.schemaVersion() == null || request.schemaVersion() != 1
                || request.deliveryId() == null || request.observedAt() == null || request.observedAt() < 0
                || request.forwarderVersion() == null || request.forwarderVersion().isBlank()
                || request.rawEvent() == null || request.rawEvent().rawJson() == null
                || !headerVersion.equals(request.forwarderVersion())) throw new IllegalArgumentException("invalid envelope");
        UUID.fromString(request.deliveryId());
        if (request.rawEvent().rawJson().getBytes(StandardCharsets.UTF_8).length > MAX_HOOK_REQUEST_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "HOOK_REQUEST_TOO_LARGE");
        }
    }
}
