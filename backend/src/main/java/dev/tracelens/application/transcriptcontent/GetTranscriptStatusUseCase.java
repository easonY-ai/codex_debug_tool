package dev.tracelens.application.transcriptcontent;

import dev.tracelens.domain.operationaldiagnostics.AuditedBusinessOperations;
import dev.tracelens.domain.transcriptcontent.TranscriptBinding;
import dev.tracelens.domain.transcriptcontent.TranscriptBindingRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** Application query for current transcript binding health. */
@Service
@AuditedBusinessOperations
public class GetTranscriptStatusUseCase {
    private final TranscriptBindingRepository transcriptBindingRepository;

    public GetTranscriptStatusUseCase(TranscriptBindingRepository transcriptBindingRepository) {
        this.transcriptBindingRepository = transcriptBindingRepository;
    }

    public List<TranscriptBinding> get() {
        return transcriptBindingRepository.findAll();
    }
}
