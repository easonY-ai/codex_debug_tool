package dev.tracelens.infrastructure.execution;

import dev.tracelens.application.execution.HookNormalizationJobRepository;
import dev.tracelens.persistence.IngestionMapper;
import org.springframework.stereotype.Repository;

/** MyBatis HookNormalizationJob 仓储，保存独立任务生命周期。 */
@Repository
public class MyBatisHookNormalizationJobRepository implements HookNormalizationJobRepository {
    private final IngestionMapper ingestionMapper;

    public MyBatisHookNormalizationJobRepository(IngestionMapper ingestionMapper) {
        this.ingestionMapper = ingestionMapper;
    }

    @Override
    public void enqueueRawHookEvent(long rawEventId, long availableAt) {
        ingestionMapper.insertNormalizationJob("HOOK", rawEventId, availableAt);
    }

    @Override
    public long countPendingNormalizations() {
        return ingestionMapper.countPendingNormalizationJobs();
    }

    @Override
    public HookNormalizationJob findNextPendingJob(long now) {
        dev.tracelens.persistence.NormalizationJob job = ingestionMapper.nextPendingNormalizationJob(now);
        return job == null ? null : new HookNormalizationJob(job.id(), job.sourceKind(), job.sourceId());
    }

    @Override
    public boolean tryStartNormalization(long id, long now) {
        return ingestionMapper.markNormalizationRunning(id, now) == 1;
    }

    @Override
    public void completeNormalization(long id, long now) {
        ingestionMapper.markNormalizationCompleted(id, now);
    }

    @Override
    public void failNormalization(long id, long now, String errorCode) {
        ingestionMapper.markNormalizationFailed(id, now, errorCode);
    }
}
