package dev.tracelens.application.hooknormalization;

import dev.tracelens.application.hookingestion.RawHookEventRepository;
import dev.tracelens.domain.hooknormalization.HookLifecycleEvent;
import dev.tracelens.domain.hooknormalization.NormalizedHookEvent;
import dev.tracelens.domain.hooknormalization.SessionLifecycle;
import dev.tracelens.domain.hooknormalization.SessionLifecycleService;
import dev.tracelens.domain.hooknormalization.SessionRepository;
import dev.tracelens.domain.hooknormalization.ToolLifecycleService;
import dev.tracelens.domain.hooknormalization.ToolRepository;
import dev.tracelens.domain.hooknormalization.TurnLifecycle;
import dev.tracelens.domain.hooknormalization.TurnLifecycleService;
import dev.tracelens.domain.hooknormalization.TurnRepository;
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

class NormalizeHookEventUseCaseTest {
    @Test
    void retriesTheWholeUseCaseAfterConcurrentAggregateCreation() {
        NormalizationJobRepository normalizationJobRepository = mock(NormalizationJobRepository.class);
        RawHookEventRepository rawHookEventRepository = mock(RawHookEventRepository.class);
        SessionRepository sessionRepository = mock(SessionRepository.class);
        TurnRepository turnRepository = mock(TurnRepository.class);
        ToolRepository toolRepository = mock(ToolRepository.class);
        HookEventParser parser = mock(HookEventParser.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);

        NormalizationJobRepository.NormalizationJob job = new NormalizationJobRepository.NormalizationJob(1, "HOOK", 7);
        when(normalizationJobRepository.nextPending(anyLong())).thenReturn(job);
        when(rawHookEventRepository.findById(7)).thenReturn(new RawHookEventRepository.RawHookEventEvidence(7, "{}", 100));
        when(parser.parse("{}", 100)).thenReturn(new NormalizedHookEvent(
                "UserPromptSubmit", "session-1", null, "turn-1", null, HookLifecycleEvent.prompt(100)));
        when(sessionRepository.findOrEmpty("session-1")).thenReturn(SessionLifecycle.empty("session-1"));
        when(turnRepository.findOrEmpty("session-1", "turn-1")).thenReturn(TurnLifecycle.empty("session-1", "turn-1"));
        doThrow(new DuplicateKeyException("concurrent create")).doNothing().when(sessionRepository).save(any());
        executeCallbacksImmediately(transactions);

        NormalizeHookEventUseCase useCase = new NormalizeHookEventUseCase(
                normalizationJobRepository, rawHookEventRepository, new SessionLifecycleService(sessionRepository),
                new TurnLifecycleService(turnRepository), new ToolLifecycleService(toolRepository),
                parser, transactions);

        useCase.processAvailable();

        verify(sessionRepository, times(2)).findOrEmpty("session-1");
        verify(sessionRepository, times(2)).save(any());
        verify(turnRepository).save(any());
        verify(normalizationJobRepository).markCompleted(eq(1L), anyLong());
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
