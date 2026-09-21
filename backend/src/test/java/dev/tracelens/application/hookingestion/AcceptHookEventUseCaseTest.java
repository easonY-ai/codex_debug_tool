package dev.tracelens.application.hookingestion;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.tracelens.application.execution.HookNormalizationJobRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AcceptHookEventUseCaseTest {
    @Test void percentileUsesNearestRankAndHandlesEmptySamples() {
        assertThat(AcceptHookEventUseCase.percentile(List.of(), .95)).isNull();
        assertThat(AcceptHookEventUseCase.percentile(List.of(1L, 2L, 3L, 4L), .50)).isEqualTo(2);
        assertThat(AcceptHookEventUseCase.percentile(List.of(1L, 2L, 3L, 4L), .95)).isEqualTo(4);
    }

    @Test
    void acceptedDeliveryLogsOnlyTheStableDiagnosticFields() {
        RawHookEventRepository rawHookEventRepository = mock(RawHookEventRepository.class);
        HookNormalizationJobRepository hookNormalizationJobRepository = mock(HookNormalizationJobRepository.class);
        TransactionTemplate transaction = mock(TransactionTemplate.class);
        when(transaction.execute(any())).thenAnswer(invocation -> invocation.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0)
                .doInTransaction(mock(TransactionStatus.class)));
        when(rawHookEventRepository.existsByDeliveryId("delivery-secret-demo")).thenReturn(false);
        when(rawHookEventRepository.append(any(), any(Long.class), any(Long.class), any(), any())).thenReturn(1L);

        Logger logger = (Logger) LoggerFactory.getLogger(AcceptHookEventUseCase.class);
        ListAppender<ILoggingEvent> records = new ListAppender<>();
        records.start();
        logger.addAppender(records);
        try {
            new AcceptHookEventUseCase(rawHookEventRepository, hookNormalizationJobRepository, transaction, (id, observedAt) -> { })
                    .accept("delivery-secret-demo", 1, "1", "raw-secret-demo");
        } finally {
            logger.detachAppender(records);
        }

        String messages = records.list.stream()
                .map(event -> event.getFormattedMessage())
                .collect(java.util.stream.Collectors.joining("\n"));
        assertThat(messages).contains("hook_delivery_accepted outcome=accepted");
        assertThat(messages).doesNotContain("delivery-secret-demo").doesNotContain("raw-secret-demo");
    }
}
