package dev.tracelens.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.sqlite.SQLiteDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigratorTest {
    @Test void upgradesLegacyOtelTableWithoutExternalServices() throws Exception {
        SQLiteDataSource source = new SQLiteDataSource(); source.setUrl("jdbc:sqlite::memory:");
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(source.getConnection(), true));
        jdbc.execute("CREATE TABLE raw_otel_object(id INTEGER PRIMARY KEY, batch_id TEXT, signal_type TEXT, received_at INTEGER, raw_json TEXT, parse_status TEXT)");
        DatabaseMigrator migrator = new DatabaseMigrator(jdbc); migrator.run(null); migrator.run(null);
        var names = jdbc.queryForList("PRAGMA table_info(raw_otel_object)").stream().map(row -> row.get("name")).toList();
        assertThat(names).contains("object_index", "turn_id", "call_id", "duration_ms", "event_time");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM schema_migration", Integer.class)).isEqualTo(1);
    }
}
