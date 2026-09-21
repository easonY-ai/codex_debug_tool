package dev.tracelens.infrastructure.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tracelens.application.execution.HookEventParser;
import dev.tracelens.domain.execution.HookLifecycleFact;
import dev.tracelens.domain.execution.NormalizedHookFact;
import dev.tracelens.domain.execution.ToolCallState;
import org.springframework.stereotype.Component;

/** Codex Hook 协议适配器；Jackson 类型不会越过 Infrastructure 边界。 */
@Component
public class JacksonHookEventParser implements HookEventParser {
    private final ObjectMapper objectMapper;

    public JacksonHookEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public NormalizedHookFact parse(String rawJson, long observedAt) {
        try {
            JsonNode event = objectMapper.readTree(rawJson);
            String name = required(event, "hook_event_name");
            String toolName = optional(event, "tool_name");
            HookLifecycleFact lifecycleFact = switch (name) {
                case "UserPromptSubmit" -> HookLifecycleFact.prompt(observedAt);
                case "Stop" -> HookLifecycleFact.stop(observedAt);
                case "Interrupt" -> HookLifecycleFact.interrupt(observedAt);
                case "PreToolUse" -> HookLifecycleFact.preTool(observedAt, toolName);
                case "PostToolUse" -> HookLifecycleFact.postTool(observedAt, toolName, postState(event));
                default -> new HookLifecycleFact(name, observedAt, toolName, null);
            };
            return new NormalizedHookFact(
                    name,
                    required(event, "session_id"),
                    optional(event, "transcript_path"),
                    optional(event, "turn_id"),
                    optional(event, "tool_use_id"),
                    optional(event, "prompt"),
                    optional(event, "model"),
                    optional(event, "cwd"),
                    lifecycleFact);
        } catch (Exception error) {
            throw new IllegalArgumentException("invalid raw Hook event", error);
        }
    }

    private static ToolCallState postState(JsonNode event) {
        JsonNode response = event.path("tool_response");
        boolean failed = response.path("exit_code").isIntegralNumber()
                && response.path("exit_code").asInt() != 0
                || response.path("is_error").asBoolean(false)
                || response.path("success").isBoolean() && !response.path("success").asBoolean();
        return failed ? ToolCallState.FAILED : ToolCallState.SUCCESS;
    }

    private static String required(JsonNode node, String field) {
        String value = optional(node, field);
        if (value == null) {
            throw new IllegalArgumentException("missing " + field);
        }
        return value;
    }

    private static String optional(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.textValue().isBlank()
                ? value.textValue()
                : null;
    }
}
