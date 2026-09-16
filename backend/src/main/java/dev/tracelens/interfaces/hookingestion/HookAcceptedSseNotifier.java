package dev.tracelens.interfaces.hookingestion;

import dev.tracelens.api.EventStreamService;
import dev.tracelens.application.hookingestion.HookAcceptedNotifier;
import dev.tracelens.domain.operationaldiagnostics.AuditedBusinessOperations;
import org.springframework.stereotype.Component;

import java.util.Map;

/** SSE adapter invoked only after the Hook acceptance transaction has committed. */
@Component
@AuditedBusinessOperations
public class HookAcceptedSseNotifier implements HookAcceptedNotifier {
    private final EventStreamService streams;
    public HookAcceptedSseNotifier(EventStreamService streams) { this.streams = streams; }
    public void accepted(String deliveryId, long observedAt) { streams.publish(Map.of("deliveryId", deliveryId, "observedAt", observedAt, "status", "ACCEPTED")); }
}
