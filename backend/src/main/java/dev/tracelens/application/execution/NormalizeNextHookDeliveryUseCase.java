package dev.tracelens.application.execution;

import dev.tracelens.application.hookingestion.RawHookEventRepository;
import dev.tracelens.domain.execution.CodexSessionService;
import dev.tracelens.domain.execution.CodexTurnService;
import dev.tracelens.domain.execution.NormalizedHookFact;
import dev.tracelens.domain.execution.ToolCallService;
import dev.tracelens.domain.operationaldiagnostics.AuditedBusinessOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Set;

/**
 * 标准化下一条 Hook delivery。单次短事务最多推进对应的 Session、Turn、ToolCall 三个小聚合；
 * 并发创建冲突会回滚整笔事务并重新读取，其他失败以稳定类别结束任务。
 */
@Service
@AuditedBusinessOperations
public class NormalizeNextHookDeliveryUseCase {
    private static final Logger logger = LoggerFactory.getLogger(NormalizeNextHookDeliveryUseCase.class);
    private static final int MAX_AGGREGATE_CONCURRENCY_ATTEMPTS = 3;
    private static final Set<String> SUPPORTED = Set.of(
            "SessionStart", "SessionEnd", "SubagentStart", "PreToolUse", "PermissionRequest",
            "PostToolUse", "PreCompact", "PostCompact", "UserPromptSubmit", "SubagentStop",
            "Stop", "Interrupt");

    private final HookNormalizationJobRepository hookNormalizationJobRepository;
    private final RawHookEventRepository rawHookEventRepository;
    private final CodexSessionService codexSessionService;
    private final CodexTurnService codexTurnService;
    private final ToolCallService toolCallService;
    private final HookEventParser hookEventParser;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Autowired
    public NormalizeNextHookDeliveryUseCase(
            HookNormalizationJobRepository hookNormalizationJobRepository,
            RawHookEventRepository rawHookEventRepository,
            CodexSessionService codexSessionService,
            CodexTurnService codexTurnService,
            ToolCallService toolCallService,
            HookEventParser hookEventParser,
            TransactionTemplate transactions) {
        this(
                hookNormalizationJobRepository,
                rawHookEventRepository,
                codexSessionService,
                codexTurnService,
                toolCallService,
                hookEventParser,
                transactions,
                Clock.systemUTC());
    }

    public NormalizeNextHookDeliveryUseCase(
            HookNormalizationJobRepository hookNormalizationJobRepository,
            RawHookEventRepository rawHookEventRepository,
            CodexSessionService codexSessionService,
            CodexTurnService codexTurnService,
            ToolCallService toolCallService,
            HookEventParser hookEventParser,
            TransactionTemplate transactions,
            Clock clock) {
        this.hookNormalizationJobRepository = hookNormalizationJobRepository;
        this.rawHookEventRepository = rawHookEventRepository;
        this.codexSessionService = codexSessionService;
        this.codexTurnService = codexTurnService;
        this.toolCallService = toolCallService;
        this.hookEventParser = hookEventParser;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * 领取并标准化一条可用 Hook delivery。无任务返回 false；成功或已稳定记录失败返回 true。
     * 聚合写入、change feed、原始事件状态和任务完成状态位于同一事务。
     */
    public boolean normalizeNextHookDelivery() {
        long now = clock.millis();
        HookNormalizationJobRepository.HookNormalizationJob job =
                hookNormalizationJobRepository.findNextPendingJob(now);
        if (job == null) {
            return false;
        }
        for (int attempt = 1; attempt <= MAX_AGGREGATE_CONCURRENCY_ATTEMPTS; attempt++) {
            try {
                transactions.executeWithoutResult(ignored -> normalize(job, now));
                return true;
            } catch (DuplicateKeyException | OptimisticLockingFailureException concurrentAggregateWrite) {
                if (attempt < MAX_AGGREGATE_CONCURRENCY_ATTEMPTS) {
                    continue;
                }
                markFailed(job, "AGGREGATE_CONCURRENCY_RETRY_EXHAUSTED");
                logger.warn("hook_normalization outcome=retry_exhausted errorCategory=aggregate_concurrency");
            } catch (RuntimeException failure) {
                markFailed(job, "NORMALIZATION_FAILED");
                logger.warn("hook_normalization outcome=failed errorCategory=normalization");
            }
            return true;
        }
        throw new IllegalStateException("normalization retry loop terminated unexpectedly");
    }

    private void normalize(HookNormalizationJobRepository.HookNormalizationJob job, long now) {
        if (!hookNormalizationJobRepository.tryStartNormalization(job.id(), now)) {
            return;
        }
        RawHookEventRepository.RawHookEventEvidence source = rawHookEventRepository.findById(job.sourceId());
        if (source == null || !"HOOK".equals(job.sourceKind())) {
            throw new IllegalStateException("missing Hook source");
        }
        NormalizedHookFact fact = hookEventParser.parse(source.rawJson(), source.observedAt());
        if (!SUPPORTED.contains(fact.name())) {
            rawHookEventRepository.updateParseStatus(source.id(), "UNKNOWN", "UNSUPPORTED_HOOK_EVENT");
            hookNormalizationJobRepository.completeNormalization(job.id(), now);
            return;
        }
        codexSessionService.applySessionFact(fact);
        codexTurnService.applyTurnFact(fact);
        toolCallService.applyToolFact(fact);
        rawHookEventRepository.updateParseStatus(source.id(), "NORMALIZED", null);
        hookNormalizationJobRepository.completeNormalization(job.id(), now);
    }

    private void markFailed(HookNormalizationJobRepository.HookNormalizationJob job, String errorCode) {
        transactions.executeWithoutResult(ignored -> {
            rawHookEventRepository.updateParseStatus(job.sourceId(), "FAILED", errorCode);
            hookNormalizationJobRepository.failNormalization(job.id(), clock.millis(), errorCode);
        });
    }
}
