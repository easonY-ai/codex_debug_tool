package dev.tracelens.infrastructure.hooknormalization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tracelens.application.hooknormalization.HookEventParser;
import dev.tracelens.domain.hooknormalization.HookLifecycleEvent;
import dev.tracelens.domain.hooknormalization.NormalizedHookEvent;
import dev.tracelens.domain.hooknormalization.ToolState;
import org.springframework.stereotype.Component;

/** Jackson-based infrastructure adapter; JsonNode never crosses into application or domain code. */
@Component
public class JacksonHookEventParser implements HookEventParser {
    private final ObjectMapper objectMapper;
    public JacksonHookEventParser(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @Override public NormalizedHookEvent parse(String rawJson, long observedAt) {
        try {
            JsonNode event = objectMapper.readTree(rawJson);
            String name = required(event, "hook_event_name");
            String sessionId = required(event, "session_id");
            String toolName = optional(event, "tool_name");
            HookLifecycleEvent lifecycle = switch (name) {
                case "UserPromptSubmit" -> HookLifecycleEvent.prompt(observedAt);
                case "Stop" -> HookLifecycleEvent.stop(observedAt);
                case "Interrupt" -> HookLifecycleEvent.interrupt(observedAt);
                case "PreToolUse" -> HookLifecycleEvent.preTool(observedAt, toolName);
                case "PostToolUse" -> HookLifecycleEvent.postTool(observedAt, toolName, postState(event));
                default -> new HookLifecycleEvent(name, observedAt, toolName, null);
            };
            return new NormalizedHookEvent(name, sessionId, optional(event, "transcript_path"), optional(event, "turn_id"),
                    optional(event, "tool_use_id"), lifecycle);
        } catch (Exception error) {
            throw new IllegalArgumentException("invalid raw Hook event", error);
        }
    }

    private static ToolState postState(JsonNode event) {
        JsonNode response = event.path("tool_response");
        return response.path("exit_code").isIntegralNumber() && response.path("exit_code").asInt() != 0
                || response.path("is_error").asBoolean(false)
                || response.path("success").isBoolean() && !response.path("success").asBoolean()
                ? ToolState.FAILED : ToolState.SUCCESS;
    }
    private static String required(JsonNode node, String field) { String value = optional(node, field); if (value == null) throw new IllegalArgumentException("missing " + field); return value; }
    private static String optional(JsonNode node, String field) { JsonNode value = node.get(field); return value != null && value.isTextual() && !value.textValue().isBlank() ? value.textValue() : null; }
}
