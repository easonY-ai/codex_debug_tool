package dev.tracelens.domain.hooknormalization;

/**
 * Entity for one (session_id, turn_id, tool_use_id) lifecycle. It accepts Pre/Post in either
 * arrival order and exposes a duration only after both chronological boundaries are known.
 */
public record ToolLifecycle(String sessionId, String turnId, String toolUseId, String toolName,
                            Long preObservedAt, Long postObservedAt, ToolState state) {
    public static ToolLifecycle empty(String sessionId, String turnId, String toolUseId) {
        return new ToolLifecycle(sessionId, turnId, toolUseId, null, null, null, ToolState.UNKNOWN);
    }

    public ToolLifecycle evolveFrom(NormalizedHookEvent event) {
        HookLifecycleEvent lifecycleEvent = event.lifecycleEvent();
        String nextToolName = toolName == null ? lifecycleEvent.toolName() : toolName;
        Long nextPre = preObservedAt;
        Long nextPost = postObservedAt;
        ToolState incoming = ToolState.UNKNOWN;
        if ("PreToolUse".equals(lifecycleEvent.name())) { nextPre = nextPre == null ? lifecycleEvent.observedAt() : nextPre; incoming = ToolState.RUNNING; }
        if ("PostToolUse".equals(lifecycleEvent.name())) { nextPost = nextPost == null ? lifecycleEvent.observedAt() : nextPost; incoming = lifecycleEvent.toolTerminalState(); }
        return new ToolLifecycle(sessionId, turnId, toolUseId, nextToolName, nextPre, nextPost, transition(state, incoming));
    }

    public boolean durationValid() { return preObservedAt != null && postObservedAt != null && postObservedAt >= preObservedAt; }
    public Long estimatedDurationMs() { return durationValid() ? postObservedAt - preObservedAt : null; }

    private static ToolState transition(ToolState current, ToolState incoming) {
        if (current.terminal()) return current;
        if (incoming != null && incoming.terminal()) return incoming;
        return current == ToolState.RUNNING ? current : incoming;
    }
}
