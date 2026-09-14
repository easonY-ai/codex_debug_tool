package dev.tracelens.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("analyzer.jsonl")
public record JsonlProperties(boolean enabled, String root,
                              @Min(100) long scanIntervalMs,
                              @Min(1024) @Max(67108864) int maxLineBytes,
                              @Min(1) @Max(1000) int batchSize) {
    public JsonlProperties {
        if (enabled && (root == null || root.isBlank())) {
            throw new IllegalArgumentException("An explicit JSONL root is required when ingestion is enabled");
        }
    }
}
