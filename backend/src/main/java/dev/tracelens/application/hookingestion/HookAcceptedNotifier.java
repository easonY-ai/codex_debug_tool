package dev.tracelens.application.hookingestion;

/** Inbound delivery has committed and may now notify read-side clients. */
public interface HookAcceptedNotifier {
    void accepted(String deliveryId, long observedAt);
}
