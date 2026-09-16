package dev.tracelens.application.transcriptcontent;

import dev.tracelens.domain.transcriptcontent.ParsedTranscriptContent;

/** One adapter decision: session metadata, attachable content, unknown shape, or ignored record. */
public record TranscriptRecordParseResult(
        Kind kind,
        String sessionId,
        ParsedTranscriptContent content,
        String unknownShape) {
    public enum Kind { SESSION_META, CONTENT, UNKNOWN, IGNORED }

    public static TranscriptRecordParseResult sessionMeta(String sessionId) {
        return new TranscriptRecordParseResult(Kind.SESSION_META, sessionId, null, null);
    }

    public static TranscriptRecordParseResult content(ParsedTranscriptContent content) {
        return new TranscriptRecordParseResult(Kind.CONTENT, null, content, null);
    }

    public static TranscriptRecordParseResult unknown(String shape) {
        return new TranscriptRecordParseResult(Kind.UNKNOWN, null, null, shape);
    }

    public static TranscriptRecordParseResult ignored() {
        return new TranscriptRecordParseResult(Kind.IGNORED, null, null, null);
    }
}
