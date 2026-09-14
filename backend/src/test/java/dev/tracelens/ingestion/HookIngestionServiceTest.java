package dev.tracelens.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HookIngestionServiceTest {
    @Test void percentileUsesNearestRankAndHandlesEmptySamples() {
        assertThat(HookIngestionService.percentile(List.of(), .95)).isNull();
        assertThat(HookIngestionService.percentile(List.of(1L, 2L, 3L, 4L), .50)).isEqualTo(2);
        assertThat(HookIngestionService.percentile(List.of(1L, 2L, 3L, 4L), .95)).isEqualTo(4);
    }
}
