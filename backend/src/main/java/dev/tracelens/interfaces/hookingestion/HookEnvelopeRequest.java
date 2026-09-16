package dev.tracelens.interfaces.hookingestion;

/** Typed HTTP contract between the Python forwarder and the Hook ingestion adapter. */
public record HookEnvelopeRequest(Integer schemaVersion, String deliveryId, Long observedAt,
                                  String forwarderVersion, RawHookEventRequest rawEvent) { }
