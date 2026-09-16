package dev.tracelens.application.hooknormalization;

/**
 * Repository for durable asynchronous normalization work. A job references a raw event but has
 * its own queue state, retry lifecycle and identity.
 */
public interface NormalizationJobRepository {
    void enqueueForRawHookEvent(long rawEventId, long availableAt);
    long countPending();
    NormalizationJob nextPending(long now);
    void markRunning(long jobId, long now);
    void markCompleted(long jobId, long now);
    void markFailed(long jobId, long now, String errorCode);

    record NormalizationJob(long id, String sourceKind, long sourceId) { }
}
