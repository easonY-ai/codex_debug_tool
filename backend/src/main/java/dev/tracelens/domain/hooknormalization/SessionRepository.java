package dev.tracelens.domain.hooknormalization;

/**
 * Domain port for the Hook session aggregate. Implementations load and persist snapshots; the
 * SessionLifecycleService owns the rule for evolving a snapshot from a Hook event.
 */
public interface SessionRepository {
    SessionLifecycle findOrEmpty(String sessionId);
    void save(SessionLifecycle session);
}
