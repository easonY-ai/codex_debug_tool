package dev.tracelens.infrastructure.execution;

import dev.tracelens.domain.execution.CodexSession;
import dev.tracelens.domain.execution.CodexSessionRepository;
import dev.tracelens.domain.execution.SessionState;
import dev.tracelens.persistence.IngestionMapper;
import org.springframework.stereotype.Repository;

import java.util.Map;

/** MyBatis CodexSession 仓储；快照和 SESSION 变更记录由调用方事务原子提交。 */
@Repository
public class MyBatisCodexSessionRepository implements CodexSessionRepository {
    private final IngestionMapper ingestionMapper;

    public MyBatisCodexSessionRepository(IngestionMapper ingestionMapper) {
        this.ingestionMapper = ingestionMapper;
    }

    @Override
    public CodexSession findSession(String sessionId) {
        Map<String, Object> row = ingestionMapper.executionSession(sessionId);
        if (row == null) {
            return CodexSession.empty(sessionId);
        }
        return new CodexSession(
                sessionId,
                text(row, "transcript_path"),
                number(row, "started_at"),
                number(row, "ended_at"),
                SessionState.valueOf(text(row, "state")),
                number(row, "last_observed_at"),
                integer(row, "version"));
    }

    @Override
    public void recordSessionState(CodexSession session) {
        if (ingestionMapper.executionSession(session.sessionId()) == null) {
            ingestionMapper.insertExecutionSession(session);
        } else {
            requireUpdated(ingestionMapper.updateExecutionSession(session));
        }
        ingestionMapper.appendExecutionChange(
                "SESSION", session.sessionId(), null, null, session.revision(), session.lastObservedAt());
    }

    static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    static Long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : ((Number) value).longValue();
    }

    static int integer(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).intValue();
    }

    static void requireUpdated(int affectedRows) {
        if (affectedRows != 1) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "Execution aggregate revision changed concurrently");
        }
    }
}
