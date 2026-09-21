package dev.tracelens;

import dev.tracelens.config.JsonlProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "trace-lens.normalization.enabled=false")
@AutoConfigureMockMvc
class DisabledIngestionTest extends MySqlIntegrationSupport {
    @Autowired MockMvc mvc;

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @org.junit.jupiter.api.BeforeEach void clearRawData() {
        jdbc.update("DELETE FROM transcript_item");
        jdbc.update("DELETE FROM transcript");
    }

    @Test void defaultsNeverReadUserSessions() throws Exception {
        mvc.perform(get("/api/ingestion/status").header("Host", "localhost"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.rawRecords").value(0)).andExpect(jsonPath("$.files").isEmpty());
        mvc.perform(post("/api/ingestion/rescan").header("Host", "localhost"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INGESTION_DISABLED"));
    }

    @Test void enabledIngestionRequiresAnExplicitDirectory() {
        assertThatThrownBy(() -> new JsonlProperties(true, "", 10000, 1024, 2)).isInstanceOf(IllegalArgumentException.class);
    }
}
