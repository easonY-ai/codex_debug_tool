package dev.tracelens.domain.transcriptcontent;

/** Result of comparing the first transcript session_meta record with its Hook session. */
public enum TranscriptSessionCheckStatus {
    PENDING,
    NOT_CHECKED,
    SESSION_META_MISSING,
    SESSION_META_UNSUPPORTED,
    SESSION_ID_MISMATCH,
    MATCHED
}
