package dev.tracelens.infrastructure.transcriptcontent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tracelens.application.transcriptcontent.TranscriptRecordParseResult;
import dev.tracelens.application.transcriptcontent.TranscriptRecordParser;
import dev.tracelens.domain.transcriptcontent.ParsedTranscriptContent;
import dev.tracelens.domain.transcriptcontent.RawTranscriptRecord;
import dev.tracelens.domain.transcriptcontent.TranscriptContentKind;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Jackson adapter for the synthetic, versioned codex-2026-09 transcript contract. */
@Component
public class JacksonTranscriptRecordParser implements TranscriptRecordParser {
    public static final String ADAPTER_VERSION = "codex-2026-09";
    private static final Set<String> KNOWN_OUTER_TYPES = Set.of(
            "session_meta", "turn_context", "event_msg", "response_item", "compacted");
    private final ObjectMapper objectMapper;

    public JacksonTranscriptRecordParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String adapterVersion() {
        return ADAPTER_VERSION;
    }

    @Override
    public TranscriptRecordParseResult parse(RawTranscriptRecord record) {
        if (!"VALID_JSON".equals(record.parseStatus())) {
            return TranscriptRecordParseResult.ignored();
        }
        try {
            JsonNode root = objectMapper.readTree(record.rawText());
            String outerType = text(root, "type");
            if ("session_meta".equals(outerType)) {
                return parseSessionMeta(root);
            }
            if (!KNOWN_OUTER_TYPES.contains(outerType)) {
                return TranscriptRecordParseResult.unknown(canonicalShape(root));
            }
            ParsedTranscriptContent content = parseKnownContent(record.id(), root);
            return content == null
                    ? TranscriptRecordParseResult.ignored()
                    : TranscriptRecordParseResult.content(content);
        } catch (Exception invalidKnownRecord) {
            return TranscriptRecordParseResult.ignored();
        }
    }

    private TranscriptRecordParseResult parseSessionMeta(JsonNode root) {
        String sessionId = text(root.path("payload"), "session_id");
        return sessionId == null
                ? TranscriptRecordParseResult.ignored()
                : TranscriptRecordParseResult.sessionMeta(sessionId);
    }

    private ParsedTranscriptContent parseKnownContent(long rawRecordId, JsonNode root) {
        JsonNode payload = root.path("payload");
        String payloadType = text(payload, "type");
        String turnId = turnId(payload);
        if (turnId == null) {
            return null;
        }

        TranscriptContentKind kind = contentKind(payload, payloadType);
        if (kind == null) {
            return null;
        }
        String content = visibleContent(payload, payloadType, kind);
        if (content == null || content.isBlank()) {
            return null;
        }
        return new ParsedTranscriptContent(
                rawRecordId,
                turnId,
                text(payload, "call_id"),
                kind,
                content,
                ADAPTER_VERSION);
    }

    private static TranscriptContentKind contentKind(JsonNode payload, String payloadType) {
        if ("user_message".equals(payloadType)) {
            return TranscriptContentKind.USER_INPUT;
        }
        if ("agent_message".equals(payloadType)) {
            return TranscriptContentKind.MODEL_OUTPUT;
        }
        if ("task_complete".equals(payloadType)) {
            return TranscriptContentKind.MODEL_OUTPUT;
        }
        if ("reasoning".equals(payloadType)) {
            return TranscriptContentKind.REASONING_SUMMARY;
        }
        if (Set.of("function_call", "custom_tool_call").contains(payloadType)) {
            return TranscriptContentKind.TOOL_INPUT;
        }
        if (Set.of("function_call_output", "custom_tool_call_output").contains(payloadType)) {
            return TranscriptContentKind.TOOL_OUTPUT;
        }
        if ("message".equals(payloadType)) {
            String role = text(payload, "role");
            if ("user".equals(role)) {
                return TranscriptContentKind.USER_INPUT;
            }
            if ("assistant".equals(role)) {
                return TranscriptContentKind.MODEL_OUTPUT;
            }
        }
        return null;
    }

    private static String visibleContent(
            JsonNode payload,
            String payloadType,
            TranscriptContentKind kind) {
        if (kind == TranscriptContentKind.REASONING_SUMMARY) {
            return joinText(payload.path("summary"));
        }
        if (kind == TranscriptContentKind.TOOL_INPUT) {
            return firstNonBlank(
                    scalarOrText(payload.get("arguments")),
                    scalarOrText(payload.get("input")));
        }
        if (kind == TranscriptContentKind.TOOL_OUTPUT) {
            return scalarOrText(payload.get("output"));
        }
        if ("task_complete".equals(payloadType)) {
            return scalarOrText(payload.get("last_agent_message"));
        }
        if ("user_message".equals(payloadType) || "agent_message".equals(payloadType)) {
            return firstNonBlank(
                    scalarOrText(payload.get("message")),
                    joinText(payload.path("content")));
        }
        return joinText(payload.path("content"));
    }

    private static String turnId(JsonNode payload) {
        String direct = text(payload, "turn_id");
        if (direct != null) {
            return direct;
        }
        return text(payload.path("internal_chat_message_metadata_passthrough"), "turn_id");
    }

    private static String scalarOrText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return node.isValueNode() ? node.asText() : joinText(node);
    }

    private static String joinText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        List<String> values = new ArrayList<>();
        collectVisibleText(node, values);
        return values.isEmpty() ? null : String.join("\n", values);
    }

    private static void collectVisibleText(JsonNode node, List<String> values) {
        if (node.isTextual()) {
            values.add(node.asText());
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectVisibleText(item, values));
            return;
        }
        if (node.isObject()) {
            JsonNode text = node.get("text");
            if (text != null && text.isTextual()) {
                values.add(text.asText());
                return;
            }
            JsonNode content = node.get("content");
            if (content != null) {
                collectVisibleText(content, values);
            }
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    static String canonicalShape(JsonNode root) {
        List<String> leaves = new ArrayList<>();
        collectShape(root, "$", leaves);
        Collections.sort(leaves);
        return String.join("\n", leaves);
    }

    private static void collectShape(JsonNode node, String path, List<String> leaves) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry ->
                    collectShape(entry.getValue(), path + "." + entry.getKey(), leaves));
            return;
        }
        if (node.isArray()) {
            node.forEach(value -> collectShape(value, path + "[*]", leaves));
            return;
        }
        String type = node.isTextual()
                ? "string"
                : node.isNumber() ? "number" : node.isBoolean() ? "boolean" : "null";
        leaves.add(path + ":" + type);
    }

    static String shapeSha256(String canonicalShape) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalShape.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
