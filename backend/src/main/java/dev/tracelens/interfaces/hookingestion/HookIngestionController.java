package dev.tracelens.interfaces.hookingestion;

import dev.tracelens.application.hookingestion.AcceptHookEventUseCase;
import dev.tracelens.domain.operationaldiagnostics.AuditedBusinessOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** HTTP adapter for typed Hook envelopes; all ingestion decisions belong to the application use case. */
@RestController
@RequestMapping("/api/ingestion")
@AuditedBusinessOperations
public class HookIngestionController {
    private static final Logger logger = LoggerFactory.getLogger(HookIngestionController.class);
    static final int MAX_HOOK_REQUEST_BYTES = 1024 * 1024;
    private final AcceptHookEventUseCase useCase;
    private final HookHttpAuditLogger auditLogger;

    public HookIngestionController(AcceptHookEventUseCase useCase, HookHttpAuditLogger auditLogger) {
        this.useCase = useCase;
        this.auditLogger = auditLogger;
    }

    @PostMapping(value = "/hooks", consumes = "application/json", produces = "application/json")
    public ResponseEntity<HookAcceptanceResponse> hook(@RequestBody HookEnvelopeRequest request,
                                                         @RequestHeader("X-Trace-Lens-Forwarder-Version") String headerVersion) {
        long started = System.nanoTime();
        try {
            auditLogger.request(request);
            validate(request, headerVersion);
            var result = useCase.accept(request.deliveryId(), request.observedAt(), request.forwarderVersion(), request.rawEvent().rawJson());
            HttpStatus status = "ACCEPTED".equals(result.status()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
            auditLogger.response(status.value(), result.status(), elapsedMillis(started));
            return ResponseEntity.status(status).body(new HookAcceptanceResponse(result.deliveryId(), result.status()));
        } catch (ResponseStatusException error) {
            auditLogger.response(error.getStatusCode().value(), "REJECTED", elapsedMillis(started));
            logger.info("hook_delivery_rejected outcome=request_too_large httpStatus={}", error.getStatusCode().value());
            throw error;
        } catch (IllegalArgumentException error) {
            useCase.recordClientError();
            auditLogger.response(HttpStatus.BAD_REQUEST.value(), "REJECTED", elapsedMillis(started));
            logger.info("hook_delivery_rejected outcome=invalid_envelope httpStatus=400");
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

    private static long elapsedMillis(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }
}
