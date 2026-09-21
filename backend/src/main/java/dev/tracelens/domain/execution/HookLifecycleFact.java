package dev.tracelens.domain.execution;

/**
 * Hook 协议经适配器归一化后的生命周期事实，只携带 Execution 状态演进所需字段。
 */
public record HookLifecycleFact(
        String name,
        long observedAt,
        String toolName,
        ToolCallState toolTerminalState) {

    public static HookLifecycleFact prompt(long observedAt) {
        return new HookLifecycleFact("UserPromptSubmit", observedAt, null, null);
    }

    public static HookLifecycleFact stop(long observedAt) {
        return new HookLifecycleFact("Stop", observedAt, null, null);
    }

    public static HookLifecycleFact interrupt(long observedAt) {
        return new HookLifecycleFact("Interrupt", observedAt, null, null);
    }

    public static HookLifecycleFact preTool(long observedAt, String toolName) {
        return new HookLifecycleFact("PreToolUse", observedAt, toolName, ToolCallState.RUNNING);
    }

    public static HookLifecycleFact postTool(long observedAt, String toolName, ToolCallState state) {
        return new HookLifecycleFact("PostToolUse", observedAt, toolName, state);
    }
}
