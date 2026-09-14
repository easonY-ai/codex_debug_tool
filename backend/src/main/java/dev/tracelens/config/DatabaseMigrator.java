package dev.tracelens.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/** Small, dependency-free migrations for databases created by earlier local builds. */
@Component
@DependsOnDatabaseInitialization
public class DatabaseMigrator implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    public DatabaseMigrator(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void run(ApplicationArguments args) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS schema_migration(version INTEGER PRIMARY KEY, applied_at INTEGER NOT NULL)");
        migrateRawOtelV2();
        jdbc.update("INSERT OR IGNORE INTO schema_migration(version, applied_at) VALUES(2, ?)", System.currentTimeMillis());
    }

    private void migrateRawOtelV2() {
        Set<String> columns = jdbc.queryForList("PRAGMA table_info(raw_otel_object)").stream()
                .map(row -> String.valueOf(row.get("name"))).collect(Collectors.toSet());
        add(columns, "object_index", "INTEGER NOT NULL DEFAULT 0");
        add(columns, "turn_id", "TEXT"); add(columns, "call_id", "TEXT");
        add(columns, "object_kind", "TEXT"); add(columns, "duration_ms", "INTEGER");
        add(columns, "event_time", "INTEGER");
    }

    private void add(Set<String> columns, String name, String definition) {
        if (!columns.contains(name)) jdbc.execute("ALTER TABLE raw_otel_object ADD COLUMN " + name + " " + definition);
    }
}
