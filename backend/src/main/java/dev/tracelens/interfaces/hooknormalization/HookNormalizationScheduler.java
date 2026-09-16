package dev.tracelens.interfaces.hooknormalization;

import dev.tracelens.application.hooknormalization.NormalizeHookEventUseCase;
import dev.tracelens.domain.operationaldiagnostics.AuditedBusinessOperations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Interface-layer scheduler: it triggers application work and owns no parsing or lifecycle rule. */
@Component
@AuditedBusinessOperations
@ConditionalOnProperty(name = "trace-lens.normalization.enabled", matchIfMissing = true)
public class HookNormalizationScheduler {
    private final NormalizeHookEventUseCase useCase;
    public HookNormalizationScheduler(NormalizeHookEventUseCase useCase) { this.useCase = useCase; }
    @Scheduled(fixedDelayString = "${trace-lens.normalization.poll-ms:100}")
    public void poll() { useCase.processAvailable(); }
}
