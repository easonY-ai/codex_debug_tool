package dev.tracelens.infrastructure.hooknormalization;

import dev.tracelens.application.hooknormalization.NormalizationJobRepository;
import dev.tracelens.persistence.IngestionMapper;
import org.springframework.stereotype.Repository;

/** MyBatis implementation of the durable normalization-job repository. */
@Repository
public class MyBatisNormalizationJobRepository implements NormalizationJobRepository {
    private final IngestionMapper mapper;

    public MyBatisNormalizationJobRepository(IngestionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void enqueueForRawHookEvent(long rawEventId, long availableAt) {
        mapper.insertNormalizationJob("HOOK", rawEventId, availableAt);
    }

    @Override
    public long countPending() {
        return mapper.countPendingNormalizationJobs();
    }

    @Override
    public NormalizationJob nextPending(long now) {
        dev.tracelens.persistence.NormalizationJob job = mapper.nextPendingNormalizationJob(now);
        return job == null ? null : new NormalizationJob(job.id(), job.sourceKind(), job.sourceId());
    }

    @Override
    public void markRunning(long jobId, long now) {
        mapper.markNormalizationRunning(jobId, now);
    }

    @Override
    public void markCompleted(long jobId, long now) {
        mapper.markNormalizationCompleted(jobId, now);
    }

    @Override
    public void markFailed(long jobId, long now, String errorCode) {
        mapper.markNormalizationFailed(jobId, now, errorCode);
    }
}
