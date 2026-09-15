package dev.tracelens.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;

/** MySQL starts with a new baseline; legacy SQLite files are never opened. */
public class DatabaseMigrator implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    public DatabaseMigrator(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void run(ApplicationArguments args) {
        jdbc.update("INSERT INTO schema_migration(version, applied_at) VALUES(1, ?) "
                + "ON DUPLICATE KEY UPDATE version=version", System.currentTimeMillis());
    }
}
