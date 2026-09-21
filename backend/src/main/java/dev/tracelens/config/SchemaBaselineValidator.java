package dev.tracelens.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** 启动时只读校验部署方显式安装的 schema baseline，不创建、升级或修正数据库对象。 */
@Component
public class SchemaBaselineValidator implements ApplicationRunner {
    static final String VERSION_QUERY = "SELECT baseline_version FROM schema_metadata";
    private static final int EXPECTED_BASELINE = 2;

    private final JdbcTemplate jdbcTemplate;

    public SchemaBaselineValidator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        final List<Integer> versions;
        try {
            versions = jdbcTemplate.queryForList(VERSION_QUERY, Integer.class);
        } catch (RuntimeException invalidSchema) {
            throw new SchemaBaselineMismatchException();
        }
        if (versions.size() != 1 || versions.get(0) == null || versions.get(0) != EXPECTED_BASELINE) {
            throw new SchemaBaselineMismatchException();
        }
    }
}
