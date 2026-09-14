package dev.tracelens.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import dev.tracelens.persistence.IngestionMapper;
import dev.tracelens.persistence.RawHookEvent;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import dev.tracelens.api.EventStreamService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class HookIngestionService {
    public record Result(String deliveryId, String status) { }
    public record Status(long checkedAt, Long lastSuccessAt, long requests, long accepted, long duplicates,
                         long clientErrors, long serverErrors, long pendingJobs,
                         Long p50ProcessingMs, Long p95ProcessingMs, Long p99ProcessingMs, Long maxProcessingMs) { }

    private final IngestionMapper mapper;
    private final TransactionTemplate transaction;
    private final EventStreamService events;
    private final List<Long> processingSamples = new ArrayList<>();
    private long requests;
    private long accepted;
    private long duplicates;
    private long clientErrors;
    private long serverErrors;
    private Long lastSuccessAt;

    public HookIngestionService(IngestionMapper mapper, TransactionTemplate transaction) { this(mapper,transaction,null); }
    @Autowired
    public HookIngestionService(IngestionMapper mapper, TransactionTemplate transaction,EventStreamService events) {
        this.mapper = mapper;
        this.transaction = transaction;
        this.events=events;
    }

    public Result accept(String deliveryId, long observedAt, String forwarderVersion, JsonNode rawEvent) {
        long started = System.nanoTime();
        synchronized (this) { requests++; }
        try {
            long receivedAt = System.currentTimeMillis();
            Result result = transaction.execute(status -> {
                RawHookEvent existing = mapper.hookEventByDeliveryId(deliveryId);
                if (existing != null) return new Result(deliveryId, "DUPLICATE");
                RawHookEvent event = new RawHookEvent(0, deliveryId, observedAt, receivedAt,
                        forwarderVersion, rawEvent.toString(), "PENDING", null);
                try {
                    mapper.insertRawHookEvent(event);
                } catch (DuplicateKeyException duplicate) {
                    return new Result(deliveryId, "DUPLICATE");
                }
                RawHookEvent inserted = mapper.hookEventByDeliveryId(deliveryId);
                mapper.insertNormalizationJob("HOOK", inserted.id(), receivedAt);
                return new Result(deliveryId, "ACCEPTED");
            });
            synchronized (this) {
                if ("ACCEPTED".equals(result.status())) accepted++; else duplicates++;
                lastSuccessAt = receivedAt;
                recordDuration(started);
            }
            if(events!=null&&"ACCEPTED".equals(result.status()))events.publish(java.util.Map.of("deliveryId",deliveryId,"observedAt",observedAt,"status","ACCEPTED"));
            return result;
        } catch (RuntimeException error) {
            synchronized (this) { serverErrors++; recordDuration(started); }
            throw error;
        }
    }

    public synchronized void recordClientError() { requests++; clientErrors++; }

    public synchronized Status status() {
        List<Long> sorted = processingSamples.stream().sorted(Comparator.naturalOrder()).toList();
        return new Status(System.currentTimeMillis(), lastSuccessAt, requests, accepted, duplicates,
                clientErrors, serverErrors, mapper.countPendingNormalizationJobs(), percentile(sorted, .50),
                percentile(sorted, .95), percentile(sorted, .99), sorted.isEmpty() ? null : sorted.getLast());
    }

    private void recordDuration(long started) {
        processingSamples.add(Math.max(0, (System.nanoTime() - started) / 1_000_000));
        if (processingSamples.size() > 2048) processingSamples.removeFirst();
    }

    static Long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) return null;
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
