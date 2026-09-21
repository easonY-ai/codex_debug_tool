package dev.tracelens.domain.execution;

/** ToolCall 聚合的持久化端口，以完整三段身份读取和记录状态。 */
public interface ToolCallRepository {
    ToolCall findToolCall(String sessionId, String turnId, String toolUseId);

    void recordToolCallState(ToolCall toolCall);
}
