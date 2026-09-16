package dev.tracelens.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeLoggingConfigurationTest {
    @Test
    void writesBackendDiagnosticsToTheRequiredRootWithSevenDayMaximum() throws IOException {
        String configuration = new String(getClass().getResourceAsStream("/application.yml").readAllBytes(), StandardCharsets.UTF_8);

        assertThat(configuration).contains("TRACE_LENS_LOG_ROOT:${user.home}/.my_logs/codex_analyze");
        assertThat(configuration).contains("/backend/trace-lens-backend.log");
        assertThat(configuration).contains("max-history: 6");
        assertThat(configuration).contains("clean-history-on-start: true");
        assertThat(configuration).contains("component=backend event=%msg");
    }
}
