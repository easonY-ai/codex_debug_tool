package dev.tracelens.domain.execution;

/** 推进单个 ToolCall 聚合的领域服务；工具事实缺失完整身份时拒绝写入。 */
public class ToolCallService {
    private final ToolCallRepository toolCallRepository;

    public ToolCallService(ToolCallRepository toolCallRepository) {
        this.toolCallRepository = toolCallRepository;
    }

    /** 应用一条 ToolCall 事实；工具事实必须具备完整三段业务身份。 */
    public void applyToolFact(NormalizedHookFact fact) {
        if (!fact.isToolFact()) {
            return;
        }
        if (fact.turnId() == null || fact.toolUseId() == null) {
            throw new IllegalArgumentException("tool fact lacks complete business identity");
        }
        ToolCall toolCall = toolCallRepository.findToolCall(
                fact.sessionId(),
                fact.turnId(),
                fact.toolUseId());
        toolCallRepository.recordToolCallState(toolCall.applyToolFact(fact));
    }
}
