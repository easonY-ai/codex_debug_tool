package dev.tracelens.infrastructure.operationaldiagnostics;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessOperationAuditAspectTest {
    @Test
    void summariesExposeOnlyTypeSizeAndStableHash() {
        String summary = BusinessOperationAuditAspect.summarizeArguments(new Object[]{
                "session-secret", new byte[]{1, 2, 3}, Map.of("prompt", "body-secret")
        });

        assertThat(summary).contains("String(length=14,sha256=");
        assertThat(summary).contains("bytes(length=3,sha256=");
        assertThat(summary).contains("Map(size=1)");
        assertThat(summary).doesNotContain("session-secret").doesNotContain("body-secret");
    }
}
