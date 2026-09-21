package dev.tracelens.domain.execution;

/**
 * Codex Turn 聚合根。业务身份是 sessionId 与 turnId 的有序组合；维护单调生命周期及列表所需结构化事实。
 * 聚合不加载 Session 或 ToolCall 集合。
 */
public record CodexTurn(
        String sessionId,
        String turnId,
        String title,
        String model,
        String workingDirectory,
        Long startedAt,
        Long endedAt,
        TurnState state,
        int revision) {

    public static CodexTurn empty(String sessionId, String turnId) {
        return new CodexTurn(sessionId, turnId, null, null, null, null, null, TurnState.UNKNOWN, 0);
    }

    public String businessIdentity() {
        return sessionId + "\u0000" + turnId;
    }

    public CodexTurn applyTurnFact(NormalizedHookFact fact) {
        HookLifecycleFact lifecycleFact = fact.lifecycleFact();
        Long nextStartedAt = startedAt;
        Long nextEndedAt = endedAt;
        TurnState incomingState = TurnState.UNKNOWN;
        if ("UserPromptSubmit".equals(lifecycleFact.name())) {
            nextStartedAt = nextStartedAt == null ? lifecycleFact.observedAt() : nextStartedAt;
            incomingState = TurnState.RUNNING;
        }
        if ("Stop".equals(lifecycleFact.name())) {
            nextEndedAt = nextEndedAt == null ? lifecycleFact.observedAt() : nextEndedAt;
            incomingState = TurnState.COMPLETED;
        }
        if ("Interrupt".equals(lifecycleFact.name())) {
            nextEndedAt = nextEndedAt == null ? lifecycleFact.observedAt() : nextEndedAt;
            incomingState = TurnState.INTERRUPTED;
        }
        return new CodexTurn(
                sessionId,
                turnId,
                firstKnown(title, fact.prompt()),
                firstKnown(model, fact.model()),
                firstKnown(workingDirectory, fact.workingDirectory()),
                nextStartedAt,
                nextEndedAt,
                transition(state, incomingState),
                revision + 1);
    }

    private static String firstKnown(String current, String incoming) {
        return current == null ? incoming : current;
    }

    private static TurnState transition(TurnState current, TurnState incoming) {
        if (current.terminal()) {
            return current;
        }
        if (incoming.terminal()) {
            return incoming;
        }
        return current == TurnState.RUNNING ? current : incoming;
    }
}
