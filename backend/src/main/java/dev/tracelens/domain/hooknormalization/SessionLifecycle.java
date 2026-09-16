package dev.tracelens.domain.hooknormalization;

/**
 * Aggregate root for a Codex session. It owns session bounds and prevents a terminal session
 * from being reopened by a delayed Hook callback.
 */
public record SessionLifecycle(String sessionId, String transcriptPath, Long startedAt, Long endedAt,
                               SessionState state, long lastObservedAt) {
    public static SessionLifecycle empty(String sessionId) {
        return new SessionLifecycle(sessionId, null, null, null, SessionState.UNKNOWN, 0);
    }

    public SessionLifecycle evolveFrom(NormalizedHookEvent event) {
        HookLifecycleEvent lifecycleEvent = event.lifecycleEvent();
        String nextPath = transcriptPath == null ? event.transcriptPath() : transcriptPath;
        Long nextStarted = startedAt;
        Long nextEnded = endedAt;
        SessionState incoming = SessionState.UNKNOWN;
        if ("SessionStart".equals(lifecycleEvent.name())) { nextStarted = nextStarted == null ? lifecycleEvent.observedAt() : nextStarted; incoming = SessionState.RUNNING; }
        if ("SessionEnd".equals(lifecycleEvent.name())) { nextEnded = nextEnded == null ? lifecycleEvent.observedAt() : nextEnded; incoming = SessionState.COMPLETED; }
        SessionState nextState = state.terminal() ? state : incoming == SessionState.COMPLETED ? incoming
                : state == SessionState.RUNNING ? state : incoming;
        return new SessionLifecycle(sessionId, nextPath, nextStarted, nextEnded, nextState, Math.max(lastObservedAt, lifecycleEvent.observedAt()));
    }
}
