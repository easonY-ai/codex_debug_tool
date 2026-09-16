package dev.tracelens.domain.hooknormalization;

/**
 * Entity for one (session_id, turn_id) lifecycle. It owns monotonic Turn state and timestamps.
 */
public record TurnLifecycle(String sessionId, String turnId, Long startedAt, Long endedAt, TurnState state) {
    public static TurnLifecycle empty(String sessionId, String turnId) {
        return new TurnLifecycle(sessionId, turnId, null, null, TurnState.UNKNOWN);
    }

    public TurnLifecycle evolveFrom(NormalizedHookEvent event) {
        HookLifecycleEvent lifecycleEvent = event.lifecycleEvent();
        Long nextStarted = startedAt;
        Long nextEnded = endedAt;
        TurnState incoming = TurnState.UNKNOWN;
        if ("UserPromptSubmit".equals(lifecycleEvent.name())) { nextStarted = nextStarted == null ? lifecycleEvent.observedAt() : nextStarted; incoming = TurnState.RUNNING; }
        if ("Stop".equals(lifecycleEvent.name())) { nextEnded = nextEnded == null ? lifecycleEvent.observedAt() : nextEnded; incoming = TurnState.COMPLETED; }
        if ("Interrupt".equals(lifecycleEvent.name())) { nextEnded = nextEnded == null ? lifecycleEvent.observedAt() : nextEnded; incoming = TurnState.INTERRUPTED; }
        return new TurnLifecycle(sessionId, turnId, nextStarted, nextEnded, transition(state, incoming));
    }

    private static TurnState transition(TurnState current, TurnState incoming) {
        if (current.terminal()) return current;
        if (incoming.terminal()) return incoming;
        return current == TurnState.RUNNING ? current : incoming;
    }
}
