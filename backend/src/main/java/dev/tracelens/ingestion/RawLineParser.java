package dev.tracelens.ingestion;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tracelens.persistence.RawRecord;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;

@Component
public class RawLineParser {
    private final ObjectMapper mapper;

    public RawLineParser(ObjectMapper mapper) { this.mapper = mapper; }

    public RawRecord parse(byte[] bytes, long sourceId, int generation, long offset, long end) {
        String text;
        String status = "VALID_JSON";
        String type = null;
        Long time = null;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            text = new String(bytes, StandardCharsets.UTF_8);
            status = "INVALID_UTF8";
        }
        if (status.equals("VALID_JSON")) {
            try {
                JsonNode node = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text);
                if (node == null || !node.isObject()) status = "INVALID_JSON";
                else {
                    if (node.path("type").isTextual()) type = node.path("type").textValue();
                    JsonNode timestamp = node.path("timestamp");
                    if (timestamp.isTextual()) {
                        try { time = Instant.parse(timestamp.textValue()).toEpochMilli(); }
                        catch (DateTimeParseException | ArithmeticException ignored) { /* Unknown event time. */ }
                    }
                }
            } catch (java.io.IOException e) {
                status = "INVALID_JSON";
            }
        }
        return new RawRecord(null, sourceId, generation, offset, end, hash(bytes), bytes, text, status,
                type, time, System.currentTimeMillis());
    }

    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
