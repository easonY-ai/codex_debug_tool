package dev.tracelens.domain.execution;

/**
 * Execution 的 Hook Published Language。原始 JSON、Jackson 类型和 HTTP 类型不得进入该值对象。
 */
public record NormalizedHookFact(
        String name,
        String sessionId,
        String transcriptPath,
        String turnId,
        String toolUseId,
        String prompt,
        String model,
        String workingDirectory,
        HookLifecycleFact lifecycleFact) {

    public boolean isToolFact() {
        return "PreToolUse".equals(name) || "PostToolUse".equals(name);
    }
}
