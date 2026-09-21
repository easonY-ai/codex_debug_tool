package dev.tracelens.application.execution;

/** Hook 标准化任务端口；方法名直接表达任务状态演进。 */
public interface HookNormalizationJobRepository {
    void enqueueRawHookEvent(long rawEventId, long availableAt);

    long countPendingNormalizations();

    HookNormalizationJob findNextPendingJob(long now);

    boolean tryStartNormalization(long id, long now);

    void completeNormalization(long id, long now);

    void failNormalization(long id, long now, String errorCode);

    record HookNormalizationJob(long id, String sourceKind, long sourceId) { }
}
