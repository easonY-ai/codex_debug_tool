package dev.tracelens.persistence;

public record RawHookEvent(long id, String deliveryId, long observedAt, long receivedAt,
                           String forwarderVersion, String rawJson, String parseStatus,
                           String errorCode) { }
