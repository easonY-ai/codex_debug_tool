package dev.tracelens.domain.transcriptcontent;

/** Structured, user-visible content emitted by a versioned transcript adapter. */
public record ParsedTranscriptContent(
        long rawRecordId,
        String turnId,
        String callId,
        TranscriptContentKind kind,
        String text,
        String adapterVersion) {
    public ParsedTranscriptContent {
        if (turnId == null || turnId.isBlank() || kind == null || adapterVersion == null) {
            throw new IllegalArgumentException("turnId, kind, and adapterVersion are required");
        }
    }
}
