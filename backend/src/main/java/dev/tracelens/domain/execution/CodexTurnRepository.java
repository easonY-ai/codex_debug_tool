package dev.tracelens.domain.execution;

/** CodexTurn 聚合的持久化端口，以完整复合身份读取和记录状态。 */
public interface CodexTurnRepository {
    CodexTurn findTurn(String sessionId, String turnId);

    void recordTurnState(CodexTurn turn);
}
