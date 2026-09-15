package dev.tracelens.config;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class DatabaseConfigurationTest {
    @Test void applicationYamlOwnsMysqlCredentialsUrlAndPoolSettings() throws IOException {
        String yaml;
        try (var input = getClass().getResourceAsStream("/application.yml")) {
            yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(yaml).contains("url: jdbc:mysql://${MYSQL_HOST:127.0.0.1}:${MYSQL_PORT:3306}/codex_analyze")
                .contains("username: ${MYSQL_USERNAME}")
                .contains("password: ${MYSQL_PASSWORD}")
                .contains("maximum-size: 4")
                .contains("connection-timeout-ms: 5000")
                .contains("session-variables: sql_mode='STRICT_ALL_TABLES,NO_ENGINE_SUBSTITUTION'");
    }
}
