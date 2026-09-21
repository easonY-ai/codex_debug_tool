package dev.tracelens.domain.execution;

/**
 * Tool Call 聚合根。业务身份是 sessionId、turnId、toolUseId；允许 Pre/Post 乱序补齐且终态不回退。
 */
public record ToolCall(
        String sessionId,
        String turnId,
        String toolUseId,
        String toolName,
        Long preObservedAt,
        Long postObservedAt,
        ToolCallState state,
        int revision) {

    public static ToolCall empty(String sessionId, String turnId, String toolUseId) {
        return new ToolCall(sessionId, turnId, toolUseId, null, null, null, ToolCallState.UNKNOWN, 0);
    }

    public ToolCall applyToolFact(NormalizedHookFact fact) {
        HookLifecycleFact lifecycleFact = fact.lifecycleFact();
        String nextToolName = toolName == null ? lifecycleFact.toolName() : toolName;
        Long nextPreObservedAt = preObservedAt;
        Long nextPostObservedAt = postObservedAt;
        ToolCallState incomingState = ToolCallState.UNKNOWN;
        if ("PreToolUse".equals(lifecycleFact.name())) {
            nextPreObservedAt = nextPreObservedAt == null ? lifecycleFact.observedAt() : nextPreObservedAt;
            incomingState = ToolCallState.RUNNING;
        }
        if ("PostToolUse".equals(lifecycleFact.name())) {
            nextPostObservedAt = nextPostObservedAt == null ? lifecycleFact.observedAt() : nextPostObservedAt;
            incomingState = lifecycleFact.toolTerminalState();
        }
        return new ToolCall(
                sessionId,
                turnId,
                toolUseId,
                nextToolName,
                nextPreObservedAt,
                nextPostObservedAt,
                transition(state, incomingState),
                revision + 1);
    }

    public boolean durationValid() {
        return preObservedAt != null && postObservedAt != null && postObservedAt >= preObservedAt;
    }

    public Long estimatedDurationMs() {
        return durationValid() ? postObservedAt - preObservedAt : null;
    }

    private static ToolCallState transition(ToolCallState current, ToolCallState incoming) {
        if (current.terminal()) {
            return current;
        }
        if (incoming != null && incoming.terminal()) {
            return incoming;
        }
        return current == ToolCallState.RUNNING ? current : incoming;
    }
}
