package dev.tracelens.domain.hooknormalization;

/**
 * A normalized Hook fact used by lifecycle rules. It deliberately contains only fields required
 * for state progression; raw JSON remains evidence at the infrastructure boundary.
 */
public record HookLifecycleEvent(String name, long observedAt, String toolName, ToolState toolTerminalState) {
    public static HookLifecycleEvent prompt(long observedAt) { return new HookLifecycleEvent("UserPromptSubmit", observedAt, null, null); }
    public static HookLifecycleEvent stop(long observedAt) { return new HookLifecycleEvent("Stop", observedAt, null, null); }
    public static HookLifecycleEvent interrupt(long observedAt) { return new HookLifecycleEvent("Interrupt", observedAt, null, null); }
    public static HookLifecycleEvent preTool(long observedAt, String toolName) { return new HookLifecycleEvent("PreToolUse", observedAt, toolName, ToolState.RUNNING); }
    public static HookLifecycleEvent postTool(long observedAt, String toolName, ToolState state) { return new HookLifecycleEvent("PostToolUse", observedAt, toolName, state); }
}
