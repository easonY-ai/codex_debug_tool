package dev.tracelens;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tracelens.ingestion.RawLineParser;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class RawLineParserTest {
    private final RawLineParser parser = new RawLineParser(new ObjectMapper());

    @Test void preservesUnknownObjectsAndOnlyUsesEventTime() {
        var row = parser.parse("{\"type\":\"future_event\",\"timestamp\":\"2026-09-01T00:00:00Z\"}".getBytes(StandardCharsets.UTF_8), 1, 0, 0, 70);
        assertThat(row.parseStatus()).isEqualTo("VALID_JSON");
        assertThat(row.eventType()).isEqualTo("future_event");
        assertThat(row.eventTime()).isEqualTo(1788220800000L);
        assertThat(parser.parse("{\"timestamp\":\"not-a-date\"}".getBytes(StandardCharsets.UTF_8), 1, 0, 0, 1).eventTime()).isNull();
    }

    @Test void rejectsTrailingObjectsPrimitivesAndMalformedJson() {
        for (String text : new String[]{"{} {}", "null", "[]", "42", "{", "", "{} garbage"}) {
            var row = parser.parse(text.getBytes(StandardCharsets.UTF_8), 1, 0, 0, 1);
            assertThat(row.parseStatus()).as(text).isEqualTo("INVALID_JSON");
            assertThat(row.rawText()).isEqualTo(text);
        }
    }

    @Test void retainsInvalidUtf8BytesWithoutAcceptingReplacementTextAsValid() {
        byte[] invalid = new byte[]{'{', '"', 'x', '"', ':', '"', (byte) 0xc3, '"', '}'};
        var row = parser.parse(invalid, 1, 0, 0, invalid.length + 1);
        assertThat(row.parseStatus()).isEqualTo("INVALID_UTF8");
        assertThat(row.rawBytes()).isEqualTo(invalid);
        assertThat(row.contentHash()).hasSize(64);
    }
}
