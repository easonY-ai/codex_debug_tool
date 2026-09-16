package dev.tracelens.interfaces.hookingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HookHttpAuditLoggerTest {
    @Test
    void summarizesInterfaceInputWithoutRetainingRawIdentifiersOrContent() {
        HookHttpAuditLogger logger = new HookHttpAuditLogger(new ObjectMapper());
        HookEnvelopeRequest request = new HookEnvelopeRequest(1, "delivery-secret-demo", 1L, "0.1.0",
                new RawHookEventRequest("{\"hook_event_name\":\"PreToolUse\",\"session_id\":\"session-secret\",\"turn_id\":\"turn-secret\",\"tool_use_id\":\"tool-secret\",\"prompt\":\"body-secret\"}"));

        HookHttpAuditLogger.HookHttpAuditSummary summary = logger.summarize(request);

        assertThat(summary.hookEventName()).isEqualTo("PreToolUse");
        assertThat(summary.bodyLength()).isPositive();
        assertThat(summary.sessionIdHash()).hasSize(64).isNotEqualTo("session-secret");
        assertThat(summary.turnIdHash()).hasSize(64).isNotEqualTo("turn-secret");
        assertThat(summary.toolUseIdHash()).hasSize(64).isNotEqualTo("tool-secret");
        assertThat(summary.deliveryIdHash()).hasSize(64).isNotEqualTo("delivery-secret-demo");
        assertThat(summary.bodySha256()).hasSize(64);
    }
}
