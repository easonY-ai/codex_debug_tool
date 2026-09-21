package dev.tracelens.infrastructure.execution;

import dev.tracelens.domain.execution.ToolCall;
import dev.tracelens.domain.execution.ToolCallRepository;
import dev.tracelens.domain.execution.ToolCallState;
import dev.tracelens.persistence.IngestionMapper;
import org.springframework.stereotype.Repository;

import java.util.Map;

/** MyBatis ToolCall 仓储；所有查询和更新都携带三段完整业务身份。 */
@Repository
public class MyBatisToolCallRepository implements ToolCallRepository {
    private final IngestionMapper ingestionMapper;

    public MyBatisToolCallRepository(IngestionMapper ingestionMapper) {
        this.ingestionMapper = ingestionMapper;
    }

    @Override
    public ToolCall findToolCall(String sessionId, String turnId, String toolUseId) {
        Map<String, Object> row = ingestionMapper.executionToolCall(sessionId, turnId, toolUseId);
        if (row == null) {
            return ToolCall.empty(sessionId, turnId, toolUseId);
        }
        return new ToolCall(
                sessionId,
                turnId,
                toolUseId,
                MyBatisCodexSessionRepository.text(row, "tool_name"),
                MyBatisCodexSessionRepository.number(row, "pre_observed_at"),
                MyBatisCodexSessionRepository.number(row, "post_observed_at"),
                ToolCallState.valueOf(MyBatisCodexSessionRepository.text(row, "state")),
                MyBatisCodexSessionRepository.integer(row, "version"));
    }

    @Override
    public void recordToolCallState(ToolCall toolCall) {
        if (ingestionMapper.executionToolCall(
                toolCall.sessionId(), toolCall.turnId(), toolCall.toolUseId()) == null) {
            ingestionMapper.insertExecutionToolCall(toolCall);
        } else {
            MyBatisCodexSessionRepository.requireUpdated(ingestionMapper.updateExecutionToolCall(toolCall));
        }
        Long changedAt = toolCall.postObservedAt() == null
                ? toolCall.preObservedAt()
                : toolCall.postObservedAt();
        ingestionMapper.appendExecutionChange(
                "TOOL_CALL", toolCall.sessionId(), toolCall.turnId(), toolCall.toolUseId(),
                toolCall.revision(), changedAt);
    }
}
