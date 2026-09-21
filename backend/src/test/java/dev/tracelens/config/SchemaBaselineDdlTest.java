package dev.tracelens.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaBaselineDdlTest {
    @Test
    void baselineTwoDeclaresEveryContextOwnedTableAndNoBaselineOneTables() throws IOException {
        String ddl;
        try (var input = getClass().getResourceAsStream("/schema.sql")) {
            ddl = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        Set<String> expectedTables = Set.of(
                "schema_metadata",
                "execution_raw_hook_event", "execution_normalization_job", "execution_session",
                "execution_turn", "execution_tool_call", "execution_change",
                "transcript", "transcript_item", "transcript_unknown_fingerprint",
                "transcript_unknown_item", "transcript_unknown_mapping", "transcript_change",
                "telemetry_record", "telemetry_change",
                "trace_transcript_evidence_link", "trace_telemetry_alignment",
                "trace_turn_view", "trace_projection_checkpoint");
        for (String table : expectedTables) {
            assertThat(ddl).contains("CREATE TABLE IF NOT EXISTS " + table + " (");
        }
        assertThat(ddl).contains("baseline_version INT NOT NULL CHECK (baseline_version = 2)");
        assertThat(ddl).doesNotContain("CREATE TABLE IF NOT EXISTS schema_migration")
                .doesNotContain("CREATE TABLE IF NOT EXISTS hook_session")
                .doesNotContain("CREATE TABLE IF NOT EXISTS transcript_binding")
                .doesNotContain("CREATE TABLE IF NOT EXISTS performance_alignment");
    }
}
