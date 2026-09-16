package dev.tracelens.application.hookingestion;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import dev.tracelens.application.hooknormalization.NormalizationJobRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Application use case for accepting a Hook delivery. It owns idempotency, the short transaction
 * and operational counters; it deliberately does not interpret the Hook event itself.
 */
@Service
public class AcceptHookEventUseCase {
    public record Result(String deliveryId, String status) { }
    public record Status(long checkedAt, Long lastSuccessAt, long requests, long accepted, long duplicates,
                         long clientErrors, long serverErrors, long pendingJobs,
                         Long p50ProcessingMs, Long p95ProcessingMs, Long p99ProcessingMs, Long maxProcessingMs) { }

    private final RawHookEventRepository rawHookEventRepository;
    private final NormalizationJobRepository normalizationJobRepository;
    private final TransactionTemplate transaction;
    private final HookAcceptedNotifier notifier;
    private final List<Long> samples = new ArrayList<>();
    private long requests, accepted, duplicates, clientErrors, serverErrors;
    private Long lastSuccessAt;

    public AcceptHookEventUseCase(RawHookEventRepository rawHookEventRepository, NormalizationJobRepository normalizationJobRepository,
                                  TransactionTemplate transaction, HookAcceptedNotifier notifier) {
        this.rawHookEventRepository = rawHookEventRepository; this.normalizationJobRepository = normalizationJobRepository;
        this.transaction = transaction; this.notifier = notifier;
    }

    public Result accept(String deliveryId, long observedAt, String forwarderVersion, String rawJson) {
        long started = System.nanoTime();
        synchronized (this) { requests++; }
        try {
            long receivedAt = System.currentTimeMillis();
            Result result = transaction.execute(status -> {
                if (rawHookEventRepository.existsByDeliveryId(deliveryId)) return new Result(deliveryId, "DUPLICATE");
                long rawEventId = rawHookEventRepository.append(deliveryId, observedAt, receivedAt, forwarderVersion, rawJson);
                normalizationJobRepository.enqueueForRawHookEvent(rawEventId, receivedAt);
                return new Result(deliveryId, "ACCEPTED");
            });
            synchronized (this) {
                if ("ACCEPTED".equals(result.status())) accepted++; else duplicates++;
                lastSuccessAt = receivedAt; recordDuration(started);
            }
            if ("ACCEPTED".equals(result.status())) notifier.accepted(deliveryId, observedAt);
            return result;
        } catch (RuntimeException failure) {
            synchronized (this) { serverErrors++; recordDuration(started); }
            throw failure;
        }
    }

    public synchronized void recordClientError() { requests++; clientErrors++; }

    public synchronized Status status() {
        List<Long> sorted = samples.stream().sorted(Comparator.naturalOrder()).toList();
        return new Status(System.currentTimeMillis(), lastSuccessAt, requests, accepted, duplicates, clientErrors, serverErrors,
                normalizationJobRepository.countPending(), percentile(sorted, .50), percentile(sorted, .95), percentile(sorted, .99),
                sorted.isEmpty() ? null : sorted.get(sorted.size() - 1));
    }

    private void recordDuration(long started) {
        samples.add(Math.max(0, (System.nanoTime() - started) / 1_000_000));
        if (samples.size() > 2048) samples.remove(0);
    }

    static Long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) return null;
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
