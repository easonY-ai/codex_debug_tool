package dev.tracelens.application.hooknormalization;

import dev.tracelens.domain.hooknormalization.NormalizedHookEvent;
import dev.tracelens.domain.hooknormalization.SessionLifecycleService;
import dev.tracelens.domain.hooknormalization.ToolLifecycleService;
import dev.tracelens.domain.hooknormalization.TurnLifecycleService;
import dev.tracelens.application.hookingestion.RawHookEventRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Set;

/**
 * Application use case for one queued raw Hook event. It orchestrates transactions and delegates
 * all lifecycle state decisions to domain models; neither a scheduler nor SQL owns those rules.
 */
@Service
public class NormalizeHookEventUseCase {
    private static final int MAX_AGGREGATE_CONCURRENCY_ATTEMPTS = 3;
    private static final Set<String> SUPPORTED = Set.of("SessionStart", "SessionEnd", "SubagentStart", "PreToolUse",
            "PermissionRequest", "PostToolUse", "PreCompact", "PostCompact", "UserPromptSubmit", "SubagentStop", "Stop", "Interrupt");
    private final NormalizationJobRepository normalizationJobRepository;
    private final RawHookEventRepository rawHookEventRepository;
    private final SessionLifecycleService sessionLifecycleService;
    private final TurnLifecycleService turnLifecycleService;
    private final ToolLifecycleService toolLifecycleService;
    private final HookEventParser parser;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Autowired
    public NormalizeHookEventUseCase(NormalizationJobRepository normalizationJobRepository, RawHookEventRepository rawHookEventRepository,
                                     SessionLifecycleService sessionLifecycleService, TurnLifecycleService turnLifecycleService,
                                     ToolLifecycleService toolLifecycleService,
                                     HookEventParser parser, TransactionTemplate transactions) {
        this(normalizationJobRepository, rawHookEventRepository, sessionLifecycleService, turnLifecycleService, toolLifecycleService, parser, transactions, Clock.systemUTC());
    }

    public NormalizeHookEventUseCase(NormalizationJobRepository normalizationJobRepository, RawHookEventRepository rawHookEventRepository,
                                     SessionLifecycleService sessionLifecycleService, TurnLifecycleService turnLifecycleService,
                                     ToolLifecycleService toolLifecycleService,
                                     HookEventParser parser, TransactionTemplate transactions, Clock clock) {
        this.normalizationJobRepository = normalizationJobRepository; this.rawHookEventRepository = rawHookEventRepository;
        this.sessionLifecycleService = sessionLifecycleService;
        this.turnLifecycleService = turnLifecycleService;
        this.toolLifecycleService = toolLifecycleService;
        this.parser = parser; this.transactions = transactions; this.clock = clock;
    }

    public boolean processAvailable() {
        long now = clock.millis();
        NormalizationJobRepository.NormalizationJob job = normalizationJobRepository.nextPending(now);
        if (job == null) return false;
        for (int attempt = 1; attempt <= MAX_AGGREGATE_CONCURRENCY_ATTEMPTS; attempt++) {
            try {
                transactions.executeWithoutResult(ignored -> normalize(job, now));
                return true;
            } catch (DuplicateKeyException concurrentAggregateCreation) {
                // The transaction has rolled back. Retry in a new transaction so the domain model
                // reads the winner's latest snapshot instead of overwriting it with a stale one.
                if (attempt < MAX_AGGREGATE_CONCURRENCY_ATTEMPTS) {
                    continue;
                }
                markFailed(job, "AGGREGATE_CONCURRENCY_RETRY_EXHAUSTED");
            } catch (RuntimeException failure) {
                markFailed(job, "NORMALIZATION_FAILED");
            }
            return true;
        }
        throw new IllegalStateException("normalization retry loop terminated unexpectedly");
    }

    private void markFailed(NormalizationJobRepository.NormalizationJob job, String errorCode) {
        transactions.executeWithoutResult(ignored -> {
            rawHookEventRepository.updateParseStatus(job.sourceId(), "FAILED", errorCode);
            normalizationJobRepository.markFailed(job.id(), clock.millis(), errorCode);
        });
    }

    private void normalize(NormalizationJobRepository.NormalizationJob job, long now) {
        normalizationJobRepository.markRunning(job.id(), now);
        RawHookEventRepository.RawHookEventEvidence source = rawHookEventRepository.findById(job.sourceId());
        if (source == null || !"HOOK".equals(job.sourceKind())) throw new IllegalStateException("missing Hook source");
        NormalizedHookEvent event = parser.parse(source.rawJson(), source.observedAt());
        if (!SUPPORTED.contains(event.name())) {
            rawHookEventRepository.updateParseStatus(source.id(), "UNKNOWN", "UNSUPPORTED_HOOK_EVENT");
            normalizationJobRepository.markCompleted(job.id(), now);
            return;
        }
        sessionLifecycleService.evolveFrom(event);
        turnLifecycleService.evolveFrom(event);
        toolLifecycleService.evolveFrom(event);
        rawHookEventRepository.updateParseStatus(source.id(), "NORMALIZED", null);
        normalizationJobRepository.markCompleted(job.id(), now);
    }
}
