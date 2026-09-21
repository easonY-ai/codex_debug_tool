package dev.tracelens.domain.execution;

/**
 * Codex Session 聚合根。业务身份是 sessionId；维护会话边界和 Hook 报告的 transcript Path 证据。
 * 终态不可被迟到事实重开，空 Path 不覆盖已知 Path；聚合不包含 Turn 集合。
 */
public record CodexSession(
        String sessionId,
        String transcriptPath,
        Long startedAt,
        Long endedAt,
        SessionState state,
        long lastObservedAt,
        int revision) {

    public static CodexSession empty(String sessionId) {
        return new CodexSession(sessionId, null, null, null, SessionState.UNKNOWN, 0, 0);
    }

    public CodexSession applySessionFact(NormalizedHookFact fact) {
        HookLifecycleFact lifecycleFact = fact.lifecycleFact();
        String nextPath = transcriptPath == null ? fact.transcriptPath() : transcriptPath;
        Long nextStartedAt = startedAt;
        Long nextEndedAt = endedAt;
        SessionState incomingState = SessionState.UNKNOWN;
        if ("SessionStart".equals(lifecycleFact.name())) {
            nextStartedAt = nextStartedAt == null ? lifecycleFact.observedAt() : nextStartedAt;
            incomingState = SessionState.RUNNING;
        }
        if ("SessionEnd".equals(lifecycleFact.name())) {
            nextEndedAt = nextEndedAt == null ? lifecycleFact.observedAt() : nextEndedAt;
            incomingState = SessionState.COMPLETED;
        }
        SessionState nextState = transition(state, incomingState);
        return new CodexSession(
                sessionId,
                nextPath,
                nextStartedAt,
                nextEndedAt,
                nextState,
                Math.max(lastObservedAt, lifecycleFact.observedAt()),
                revision + 1);
    }

    private static SessionState transition(SessionState current, SessionState incoming) {
        if (current.terminal()) {
            return current;
        }
        if (incoming.terminal()) {
            return incoming;
        }
        return current == SessionState.RUNNING ? current : incoming;
    }
}
