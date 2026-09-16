package dev.tracelens.domain.hooknormalization;

/**
 * Domain service for evolving one Session aggregate from a normalized Hook fact.
 *
 * <p>It owns the complete Session update operation: retrieve the current aggregate, apply its
 * lifecycle invariants, and persist the resulting snapshot. It does not coordinate Turn, Tool,
 * scheduling, or transaction boundaries.</p>
 */
public class SessionLifecycleService {
    private final SessionRepository sessionRepository;

    public SessionLifecycleService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public void evolveFrom(NormalizedHookEvent event) {
        SessionLifecycle currentSession = sessionRepository.findOrEmpty(event.sessionId());
        SessionLifecycle updatedSession = currentSession.evolveFrom(event);
        sessionRepository.save(updatedSession);
    }
}
