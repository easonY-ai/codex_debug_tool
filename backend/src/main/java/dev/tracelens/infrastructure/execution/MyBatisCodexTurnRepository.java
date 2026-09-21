package dev.tracelens.infrastructure.execution;

import dev.tracelens.domain.execution.CodexTurn;
import dev.tracelens.domain.execution.CodexTurnRepository;
import dev.tracelens.domain.execution.TurnState;
import dev.tracelens.persistence.IngestionMapper;
import org.springframework.stereotype.Repository;

import java.util.Map;

/** MyBatis CodexTurn 仓储；所有查询和更新都携带 sessionId 与 turnId。 */
@Repository
public class MyBatisCodexTurnRepository implements CodexTurnRepository {
    private final IngestionMapper ingestionMapper;

    public MyBatisCodexTurnRepository(IngestionMapper ingestionMapper) {
        this.ingestionMapper = ingestionMapper;
    }

    @Override
    public CodexTurn findTurn(String sessionId, String turnId) {
        Map<String, Object> row = ingestionMapper.executionTurn(sessionId, turnId);
        if (row == null) {
            return CodexTurn.empty(sessionId, turnId);
        }
        return new CodexTurn(
                sessionId,
                turnId,
                MyBatisCodexSessionRepository.text(row, "title"),
                MyBatisCodexSessionRepository.text(row, "model"),
                MyBatisCodexSessionRepository.text(row, "working_directory"),
                MyBatisCodexSessionRepository.number(row, "started_at"),
                MyBatisCodexSessionRepository.number(row, "ended_at"),
                TurnState.valueOf(MyBatisCodexSessionRepository.text(row, "state")),
                MyBatisCodexSessionRepository.integer(row, "version"));
    }

    @Override
    public void recordTurnState(CodexTurn turn) {
        if (ingestionMapper.executionTurn(turn.sessionId(), turn.turnId()) == null) {
            ingestionMapper.insertExecutionTurn(turn);
        } else {
            MyBatisCodexSessionRepository.requireUpdated(ingestionMapper.updateExecutionTurn(turn));
        }
        ingestionMapper.appendExecutionChange(
                "TURN", turn.sessionId(), turn.turnId(), null, turn.revision(),
                turn.endedAt() == null ? turn.startedAt() : turn.endedAt());
    }
}
