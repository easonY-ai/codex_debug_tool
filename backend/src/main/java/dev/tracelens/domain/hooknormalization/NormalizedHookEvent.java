package dev.tracelens.domain.hooknormalization;

/**
 * Value object produced from raw Hook evidence. It is intentionally independent of Jackson and
 * HTTP, so lifecycle rules can be tested without transport or JSON fixtures.
 */
public record NormalizedHookEvent(String name, String sessionId, String transcriptPath, String turnId,
                                  String toolUseId, HookLifecycleEvent lifecycleEvent) {
    public boolean isToolEvent() { return "PreToolUse".equals(name) || "PostToolUse".equals(name); }
}
