package dev.tracelens.infrastructure.hooknormalization;

import dev.tracelens.domain.hooknormalization.TurnLifecycle;
import dev.tracelens.domain.hooknormalization.TurnRepository;
import dev.tracelens.domain.hooknormalization.TurnState;
import dev.tracelens.persistence.IngestionMapper;
import org.springframework.stereotype.Repository;

import java.util.Map;

/** MyBatis implementation of the Hook turn repository. */
@Repository
public class MyBatisTurnRepository implements TurnRepository {
    private final IngestionMapper mapper;
    public MyBatisTurnRepository(IngestionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public TurnLifecycle findOrEmpty(String sessionId, String turnId) {
        Map<String, Object> row = mapper.hookTurn(sessionId, turnId);
        if (row == null) {
            return TurnLifecycle.empty(sessionId, turnId);
        }
        return new TurnLifecycle(sessionId, turnId, number(row, "started_at"), number(row, "ended_at"),
                TurnState.valueOf(text(row, "state")));
    }

    @Override
    public void save(TurnLifecycle turn) {
        if (mapper.hookTurn(turn.sessionId(), turn.turnId()) != null) {
            mapper.updateHookTurn(turn);
            return;
        }
        mapper.insertHookTurn(turn);
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
