package dev.tracelens.domain.transcriptcontent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptBindingTest {
    @Test
    void onlyMatchedValidBindingAllowsContentSupplement() {
        TranscriptBinding matched = TranscriptBinding.checked(
                "session-demo", "/workspace/demo.jsonl", "/workspace/demo.jsonl",
                7L, 11L, "session-demo", TranscriptSessionCheckStatus.MATCHED,
                "codex-2026-09", 100L);
        TranscriptBinding mismatch = TranscriptBinding.checked(
                "session-demo", "/workspace/demo.jsonl", "/workspace/demo.jsonl",
                7L, 11L, "another-session", TranscriptSessionCheckStatus.SESSION_ID_MISMATCH,
                "codex-2026-09", 100L);
        TranscriptBinding missing = TranscriptBinding.pathFailure(
                "session-demo", "/workspace/missing.jsonl", TranscriptPathStatus.MISSING, 100L);

        assertThat(matched.allowsContentSupplement()).isTrue();
        assertThat(mismatch.allowsContentSupplement()).isFalse();
        assertThat(missing.allowsContentSupplement()).isFalse();
    }
}
