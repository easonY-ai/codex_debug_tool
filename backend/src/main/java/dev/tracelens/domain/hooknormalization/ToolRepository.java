package dev.tracelens.domain.hooknormalization;

/** Domain port for a Hook tool-call entity within a turn. */
public interface ToolRepository {
    ToolLifecycle findOrEmpty(String sessionId, String turnId, String toolUseId);
    void save(ToolLifecycle tool);
}
