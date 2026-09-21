package dev.tracelens.application.execution;

import dev.tracelens.application.hookingestion.RawHookEventRepository;
import dev.tracelens.domain.execution.CodexSession;
import dev.tracelens.domain.execution.CodexSessionRepository;
import dev.tracelens.domain.execution.CodexSessionService;
import dev.tracelens.domain.execution.CodexTurn;
import dev.tracelens.domain.execution.CodexTurnRepository;
import dev.tracelens.domain.execution.CodexTurnService;
import dev.tracelens.domain.execution.HookLifecycleFact;
import dev.tracelens.domain.execution.NormalizedHookFact;
import dev.tracelens.domain.execution.ToolCallRepository;
import dev.tracelens.domain.execution.ToolCallService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NormalizeNextHookDeliveryUseCaseTest {
    @Test
    void retriesTheWholeBoundedTransactionAfterConcurrentAggregateCreation() {
        HookNormalizationJobRepository hookNormalizationJobRepository = mock(HookNormalizationJobRepository.class);
        RawHookEventRepository rawHookEventRepository = mock(RawHookEventRepository.class);
        CodexSessionRepository codexSessionRepository = mock(CodexSessionRepository.class);
        CodexTurnRepository codexTurnRepository = mock(CodexTurnRepository.class);
        ToolCallRepository toolCallRepository = mock(ToolCallRepository.class);
        HookEventParser parser = mock(HookEventParser.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);

        HookNormalizationJobRepository.HookNormalizationJob job =
                new HookNormalizationJobRepository.HookNormalizationJob(1, "HOOK", 7);
        when(hookNormalizationJobRepository.findNextPendingJob(anyLong())).thenReturn(job);
        when(hookNormalizationJobRepository.tryStartNormalization(eq(1L), anyLong())).thenReturn(true);
        when(rawHookEventRepository.findById(7))
                .thenReturn(new RawHookEventRepository.RawHookEventEvidence(7, "{}", 100));
        when(parser.parse("{}", 100)).thenReturn(new NormalizedHookFact(
                "UserPromptSubmit", "session-1", null, "turn-1", null,
                "Demo", "gpt-demo", "/workspace/demo-project", HookLifecycleFact.prompt(100)));
        when(codexSessionRepository.findSession("session-1")).thenReturn(CodexSession.empty("session-1"));
        when(codexTurnRepository.findTurn("session-1", "turn-1"))
                .thenReturn(CodexTurn.empty("session-1", "turn-1"));
        doThrow(new DuplicateKeyException("concurrent create")).doNothing()
                .when(codexSessionRepository).recordSessionState(any());
        executeCallbacksImmediately(transactions);

        NormalizeNextHookDeliveryUseCase useCase = new NormalizeNextHookDeliveryUseCase(
                hookNormalizationJobRepository,
                rawHookEventRepository,
                new CodexSessionService(codexSessionRepository),
                new CodexTurnService(codexTurnRepository),
                new ToolCallService(toolCallRepository),
                parser,
                transactions);

        useCase.normalizeNextHookDelivery();

        verify(codexSessionRepository, times(2)).findSession("session-1");
        verify(codexSessionRepository, times(2)).recordSessionState(any());
        verify(codexTurnRepository).recordTurnState(any());
        verify(hookNormalizationJobRepository).completeNormalization(eq(1L), anyLong());
    }

    @Test
    void workerThatLosesTheAtomicJobClaimDoesNotTouchExecutionAggregates() {
        HookNormalizationJobRepository hookNormalizationJobRepository = mock(HookNormalizationJobRepository.class);
        RawHookEventRepository rawHookEventRepository = mock(RawHookEventRepository.class);
        CodexSessionRepository codexSessionRepository = mock(CodexSessionRepository.class);
        CodexTurnRepository codexTurnRepository = mock(CodexTurnRepository.class);
        ToolCallRepository toolCallRepository = mock(ToolCallRepository.class);
        HookEventParser parser = mock(HookEventParser.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        HookNormalizationJobRepository.HookNormalizationJob job =
                new HookNormalizationJobRepository.HookNormalizationJob(1, "HOOK", 7);
        when(hookNormalizationJobRepository.findNextPendingJob(anyLong())).thenReturn(job);
        when(hookNormalizationJobRepository.tryStartNormalization(eq(1L), anyLong())).thenReturn(false);
        executeCallbacksImmediately(transactions);

        NormalizeNextHookDeliveryUseCase useCase = new NormalizeNextHookDeliveryUseCase(
                hookNormalizationJobRepository,
                rawHookEventRepository,
                new CodexSessionService(codexSessionRepository),
                new CodexTurnService(codexTurnRepository),
                new ToolCallService(toolCallRepository),
                parser,
                transactions);

        useCase.normalizeNextHookDelivery();

        verify(hookNormalizationJobRepository).tryStartNormalization(eq(1L), anyLong());
        org.mockito.Mockito.verifyNoInteractions(
                rawHookEventRepository,
                codexSessionRepository,
                codexTurnRepository,
                toolCallRepository,
                parser);
    }

    @SuppressWarnings("unchecked")
    private static void executeCallbacksImmediately(TransactionTemplate transactions) {
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
    }
}
