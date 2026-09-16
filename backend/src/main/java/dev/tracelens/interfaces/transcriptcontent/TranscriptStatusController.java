package dev.tracelens.interfaces.transcriptcontent;

import dev.tracelens.application.transcriptcontent.GetTranscriptStatusUseCase;
import dev.tracelens.domain.operationaldiagnostics.AuditedBusinessOperations;
import dev.tracelens.domain.transcriptcontent.TranscriptBinding;
import dev.tracelens.domain.transcriptcontent.TranscriptPathStatus;
import dev.tracelens.domain.transcriptcontent.TranscriptSessionCheckStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** HTTP projection of transcript health; local paths and raw content are intentionally omitted. */
@RestController
@RequestMapping("/api/ingestion/transcripts")
@AuditedBusinessOperations
public class TranscriptStatusController {
    private final GetTranscriptStatusUseCase useCase;

    public TranscriptStatusController(GetTranscriptStatusUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/status")
    public TranscriptStatusResponse status() {
        List<TranscriptBindingItemResponse> items = useCase.get().stream()
                .map(TranscriptBindingItemResponse::from)
                .toList();
        return new TranscriptStatusResponse(items, items.size());
    }

    public record TranscriptStatusResponse(
            List<TranscriptBindingItemResponse> items,
            int total) { }

    public record TranscriptBindingItemResponse(
            String sessionId,
            TranscriptPathStatus pathStatus,
            TranscriptSessionCheckStatus sessionCheckStatus,
            String adapterVersion,
            long checkedAt) {
        static TranscriptBindingItemResponse from(TranscriptBinding binding) {
            return new TranscriptBindingItemResponse(
                    binding.sessionId(),
                    binding.pathStatus(),
                    binding.sessionCheckStatus(),
                    binding.adapterVersion(),
                    binding.checkedAt());
        }
    }
}
