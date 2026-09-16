package dev.tracelens.interfaces.transcriptcontent;

import dev.tracelens.application.transcriptcontent.SupplementTranscriptContentUseCase;
import dev.tracelens.domain.operationaldiagnostics.AuditedBusinessOperations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Interface-layer scheduler that owns no file, JSON, association, or persistence rule. */
@Component
@AuditedBusinessOperations
@ConditionalOnProperty(name = "trace-lens.transcripts.enabled", matchIfMissing = true)
public class TranscriptContentScheduler {
    private final SupplementTranscriptContentUseCase useCase;

    public TranscriptContentScheduler(SupplementTranscriptContentUseCase useCase) {
        this.useCase = useCase;
    }

    @Scheduled(fixedDelayString = "${trace-lens.transcripts.poll-ms:1000}")
    public void poll() {
        useCase.processAvailable();
    }
}
