package dev.tracelens.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DatabaseMigratorTest {
    @Test void recordsMysqlBaselineWithoutOpeningLegacyFiles() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new DatabaseMigrator(jdbc).run(null);
        verify(jdbc).update(eq("INSERT INTO schema_migration(version, applied_at) VALUES(1, ?) "
                + "ON DUPLICATE KEY UPDATE version=version"), anyLong());
        verifyNoMoreInteractions(jdbc);
    }
}
