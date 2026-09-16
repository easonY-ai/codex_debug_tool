package dev.tracelens.infrastructure.transcriptcontent;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tracelens.application.transcriptcontent.TranscriptRecordParseResult;
import dev.tracelens.domain.transcriptcontent.RawTranscriptRecord;
import dev.tracelens.domain.transcriptcontent.TranscriptContentKind;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonTranscriptRecordParserTest {
    private final JacksonTranscriptRecordParser parser = new JacksonTranscriptRecordParser(new ObjectMapper());

    @Test
    void parsesTheVersionedSyntheticContract() throws Exception {
        Path fixture = Path.of("..", "prd_and_design", "fixtures", "transcripts",
                "codex-2026-09", "main-flow.jsonl");
        List<String> lines = Files.readAllLines(fixture);

        var meta = parser.parse(new RawTranscriptRecord(1, "VALID_JSON", lines.get(0)));
        assertThat(meta.kind()).isEqualTo(TranscriptRecordParseResult.Kind.SESSION_META);
        assertThat(meta.sessionId()).isEqualTo("session-demo-transcript-002");

        List<TranscriptRecordParseResult> content = java.util.stream.IntStream.range(1, lines.size())
                .mapToObj(index -> parser.parse(new RawTranscriptRecord(index + 1, "VALID_JSON", lines.get(index))))
                .toList();
        assertThat(content).extracting(result -> result.content().kind()).containsExactly(
                TranscriptContentKind.USER_INPUT,
                TranscriptContentKind.TOOL_INPUT,
                TranscriptContentKind.TOOL_OUTPUT,
                TranscriptContentKind.REASONING_SUMMARY,
                TranscriptContentKind.MODEL_OUTPUT);
        assertThat(content.get(0).content().turnId()).isEqualTo("turn-demo-transcript-002");
        assertThat(content.get(1).content().turnId()).isEqualTo("turn-jsonl-internal-002");
        assertThat(content.subList(2, content.size())).allSatisfy(result ->
                assertThat(result.content().turnId()).isEqualTo("turn-demo-transcript-002"));
        assertThat(content.get(1).content().callId()).isEqualTo("tool-demo-transcript-002");
        assertThat(content.get(2).content().text()).contains("trace-lens-transcript-002");
        assertThat(content.get(4).content().text()).isEqualTo("trace-lens-transcript-002");
    }

    @Test
    void doesNotTreatInvalidOrKnownMetadataAsUnknownContent() {
        assertThat(parser.parse(new RawTranscriptRecord(1, "INVALID_JSON", "not-json")).kind())
                .isEqualTo(TranscriptRecordParseResult.Kind.IGNORED);
        assertThat(parser.parse(new RawTranscriptRecord(2, "VALID_JSON", """
                {"type":"turn_context","payload":{"turn_id":"turn-demo"}}
                """)).kind()).isEqualTo(TranscriptRecordParseResult.Kind.IGNORED);
    }
}
