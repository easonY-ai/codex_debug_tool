package dev.tracelens.application.hookingestion;

/**
 * Repository for preserved Hook delivery evidence. Its identity is the forwarder's delivery ID;
 * it does not schedule or normalize the evidence.
 */
public interface RawHookEventRepository {
    boolean existsByDeliveryId(String deliveryId);
    long append(String deliveryId, long observedAt, long receivedAt, String forwarderVersion, String rawJson);
    RawHookEventEvidence findById(long id);
    void updateParseStatus(long id, String status, String errorCode);

    record RawHookEventEvidence(long id, String rawJson, long observedAt) { }
}
