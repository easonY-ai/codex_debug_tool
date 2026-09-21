package dev.tracelens.domain.execution;

/** CodexSession 聚合的持久化端口；记录状态时必须在同一事务追加 Execution Change。 */
public interface CodexSessionRepository {
    CodexSession findSession(String sessionId);

    void recordSessionState(CodexSession session);
}
