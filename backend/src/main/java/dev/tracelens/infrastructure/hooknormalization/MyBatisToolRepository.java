package dev.tracelens.infrastructure.hooknormalization;

import dev.tracelens.domain.hooknormalization.ToolLifecycle;
import dev.tracelens.domain.hooknormalization.ToolRepository;
import dev.tracelens.domain.hooknormalization.ToolState;
import dev.tracelens.persistence.IngestionMapper;
import org.springframework.stereotype.Repository;

import java.util.Map;

/** MyBatis implementation of the Hook tool-call repository. */
@Repository
public class MyBatisToolRepository implements ToolRepository {
    private final IngestionMapper mapper;
    public MyBatisToolRepository(IngestionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ToolLifecycle findOrEmpty(String sessionId, String turnId, String toolUseId) {
        Map<String, Object> row = mapper.hookTool(sessionId, turnId, toolUseId);
        if (row == null) {
            return ToolLifecycle.empty(sessionId, turnId, toolUseId);
        }
        return new ToolLifecycle(
                sessionId, turnId, toolUseId, text(row, "tool_name"), number(row, "pre_observed_at"),
                number(row, "post_observed_at"), ToolState.valueOf(text(row, "state")));
    }

    @Override
    public void save(ToolLifecycle tool) {
        if (mapper.hookTool(tool.sessionId(), tool.turnId(), tool.toolUseId()) != null) {
            mapper.updateHookTool(tool);
            return;
        }
        mapper.insertHookTool(tool);
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
