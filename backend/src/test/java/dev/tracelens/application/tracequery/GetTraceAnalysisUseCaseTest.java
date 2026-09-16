package dev.tracelens.application.tracequery;

import dev.tracelens.domain.tracequery.TraceAnalysisRepository;
import dev.tracelens.domain.tracequery.TraceAnalysisRepository.TraceTool;
import dev.tracelens.domain.tracequery.TraceAnalysisRepository.TraceTurn;
import dev.tracelens.domain.transcriptcontent.JsonlSupplementRepository;
import dev.tracelens.domain.transcriptcontent.TranscriptContentKind;
import dev.tracelens.domain.transcriptcontent.TranscriptMappingLevel;
import dev.tracelens.domain.transcriptcontent.TranscriptSupplementEvidence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetTraceAnalysisUseCaseTest {
    @Test
    void assemblesHookTimingAndTranscriptCoverageWithoutInventingOtel() {
        TraceAnalysisRepository traceAnalysisRepository = mock(TraceAnalysisRepository.class);
        JsonlSupplementRepository jsonlSupplementRepository = mock(JsonlSupplementRepository.class);
        when(traceAnalysisRepository.findTurn("turn-demo")).thenReturn(new TraceTurn(
                "session-demo", "turn-demo", 1000L, 6000L, "COMPLETED",
                "COMPLETED", "VALID", "MATCHED", 1, 0));
        when(traceAnalysisRepository.findTools("turn-demo")).thenReturn(List.of(new TraceTool(
                "session-demo", "turn-demo", "tool-demo", "Bash", 2000L, 5000L,
                "SUCCESS", 3000L, 1, 1)));
        when(traceAnalysisRepository.findHookEvents("turn-demo")).thenReturn(List.of());
        when(traceAnalysisRepository.findPerformanceSpans("turn-demo")).thenReturn(List.of());
        when(traceAnalysisRepository.findAlignments("turn-demo")).thenReturn(List.of());
        when(jsonlSupplementRepository.findForTurn("turn-demo")).thenReturn(List.of(
                new TranscriptSupplementEvidence(
                        1, "TURN", "turn-demo", 9, TranscriptContentKind.USER_INPUT,
                        null, TranscriptMappingLevel.BOUNDED, "codex-2026-09",
                        "Synthetic question", "{}")));

        TraceAnalysisResult result = new GetTraceAnalysisUseCase(
                traceAnalysisRepository,
                jsonlSupplementRepository).get("turn-demo");

        assertThat(result.aggregates()).satisfies(aggregates -> {
            assertThat(aggregates.totalMs()).isEqualTo(5000);
            assertThat(aggregates.toolMs()).isEqualTo(3000);
            assertThat(aggregates.unattributedMs()).isEqualTo(2000);
            assertThat(aggregates.otelStatus()).isEqualTo("MISSING");
            assertThat(aggregates.contentStatus()).isEqualTo("AVAILABLE");
        });
    }
}
