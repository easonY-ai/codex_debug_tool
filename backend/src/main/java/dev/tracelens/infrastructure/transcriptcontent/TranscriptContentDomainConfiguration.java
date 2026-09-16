package dev.tracelens.infrastructure.transcriptcontent;

import dev.tracelens.domain.transcriptcontent.HookTranscriptTargetRepository;
import dev.tracelens.domain.transcriptcontent.JsonlSupplementRepository;
import dev.tracelens.domain.transcriptcontent.TranscriptContentService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the pure transcript-content domain service. */
@Configuration
public class TranscriptContentDomainConfiguration {
    @Bean
    TranscriptContentService transcriptContentService(
            HookTranscriptTargetRepository hookTranscriptTargetRepository,
            JsonlSupplementRepository jsonlSupplementRepository) {
        return new TranscriptContentService(
                hookTranscriptTargetRepository,
                jsonlSupplementRepository);
    }
}
