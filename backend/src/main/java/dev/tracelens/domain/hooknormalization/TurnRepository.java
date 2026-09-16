package dev.tracelens.domain.hooknormalization;

/** Domain port for a Hook turn entity within its session. */
public interface TurnRepository {
    TurnLifecycle findOrEmpty(String sessionId, String turnId);
    void save(TurnLifecycle turn);
}
