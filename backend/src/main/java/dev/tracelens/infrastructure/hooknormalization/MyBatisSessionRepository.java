package dev.tracelens.infrastructure.hooknormalization;

import dev.tracelens.domain.hooknormalization.SessionLifecycle;
import dev.tracelens.domain.hooknormalization.SessionRepository;
import dev.tracelens.domain.hooknormalization.SessionState;
import dev.tracelens.persistence.IngestionMapper;
import org.springframework.stereotype.Repository;

import java.util.Map;

/** MyBatis implementation of the Hook session aggregate repository. */
@Repository
public class MyBatisSessionRepository implements SessionRepository {
    private final IngestionMapper mapper;
    public MyBatisSessionRepository(IngestionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public SessionLifecycle findOrEmpty(String id) {
        Map<String, Object> row = mapper.hookSession(id);
        if (row == null) {
            return SessionLifecycle.empty(id);
        }
        return new SessionLifecycle(
                id, text(row, "transcript_path"), number(row, "started_at"), number(row, "ended_at"),
                SessionState.valueOf(text(row, "state")), number(row, "last_observed_at"));
    }

    @Override
    public void save(SessionLifecycle session) {
        if (mapper.hookSession(session.sessionId()) != null) {
            mapper.updateHookSession(session);
            return;
        }
        mapper.insertHookSession(session);
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private static Long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : ((Number) value).longValue();
    }
}
