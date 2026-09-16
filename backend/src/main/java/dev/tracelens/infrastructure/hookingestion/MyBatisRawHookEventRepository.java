package dev.tracelens.infrastructure.hookingestion;

import dev.tracelens.application.hookingestion.RawHookEventRepository;
import dev.tracelens.persistence.IngestionMapper;
import dev.tracelens.persistence.RawHookEvent;
import org.springframework.stereotype.Repository;

/** MyBatis implementation of the raw Hook evidence repository. */
@Repository
public class MyBatisRawHookEventRepository implements RawHookEventRepository {
    private final IngestionMapper mapper;

    public MyBatisRawHookEventRepository(IngestionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean existsByDeliveryId(String deliveryId) {
        return mapper.hookEventByDeliveryId(deliveryId) != null;
    }

    @Override
    public long append(String deliveryId, long observedAt, long receivedAt, String forwarderVersion, String rawJson) {
        RawHookEvent event = new RawHookEvent(
                0, deliveryId, observedAt, receivedAt, forwarderVersion, rawJson, "PENDING", null);
        mapper.insertRawHookEvent(event);
        return event.id();
    }

    @Override
    public RawHookEventEvidence findById(long id) {
        RawHookEvent event = mapper.hookEventById(id);
        return event == null ? null : new RawHookEventEvidence(event.id(), event.rawJson(), event.observedAt());
    }

    @Override
    public void updateParseStatus(long id, String status, String errorCode) {
        mapper.updateHookParseStatus(id, status, errorCode);
    }
}
