package dev.tracelens.interfaces.hookingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Interface-layer diagnostic logger for the Hook HTTP contract.
 *
 * <p>It deliberately logs only contract metadata and stable SHA-256 summaries. Raw Hook content
 * remains evidence data and must never be formatted into an operational log.</p>
 */
@Component
public class HookHttpAuditLogger {
    private static final Logger logger = LoggerFactory.getLogger(HookHttpAuditLogger.class);
    private final ObjectMapper objectMapper;

    public HookHttpAuditLogger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void request(HookEnvelopeRequest request) {
        HookHttpAuditSummary summary = summarize(request);
        logger.info("hook_http_request outcome=received schemaVersion={} forwarderVersion={} hookEventName={} bodyLength={} bodySha256={} sessionIdHash={} turnIdHash={} toolUseIdHash={} deliveryIdHash={}",
                summary.schemaVersion(), summary.forwarderVersion(), summary.hookEventName(), summary.bodyLength(),
                summary.bodySha256(), summary.sessionIdHash(), summary.turnIdHash(), summary.toolUseIdHash(),
                summary.deliveryIdHash());
    }

    public void response(int httpStatus, String result, long durationMs) {
        logger.info("hook_http_response outcome={} httpStatus={} result={} durationMs={}", result, httpStatus, result, durationMs);
    }

    HookHttpAuditSummary summarize(HookEnvelopeRequest request) {
        String rawJson = request == null || request.rawEvent() == null ? null : request.rawEvent().rawJson();
        JsonNode rawEvent = parse(rawJson);
        return new HookHttpAuditSummary(
                request == null ? null : request.schemaVersion(),
                request == null ? null : request.forwarderVersion(),
                text(rawEvent, "hook_event_name"),
                rawJson == null ? 0 : rawJson.getBytes(StandardCharsets.UTF_8).length,
                hash(rawJson),
                hash(text(rawEvent, "session_id")),
                hash(text(rawEvent, "turn_id")),
                hash(text(rawEvent, "tool_use_id")),
                hash(request == null ? null : request.deliveryId()));
    }

    private JsonNode parse(String rawJson) {
        if (rawJson == null) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.readTree(rawJson);
        } catch (Exception ignored) {
            return objectMapper.nullNode();
        }
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && field.isTextual() ? field.textValue() : null;
    }

    private static String hash(String value) {
        if (value == null || value.isBlank()) {
            return "absent";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    record HookHttpAuditSummary(Integer schemaVersion, String forwarderVersion, String hookEventName,
                                int bodyLength, String bodySha256, String sessionIdHash,
                                String turnIdHash, String toolUseIdHash, String deliveryIdHash) { }
}
