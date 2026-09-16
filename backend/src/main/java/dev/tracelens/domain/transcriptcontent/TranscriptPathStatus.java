package dev.tracelens.domain.transcriptcontent;

/** Safety and availability result for a Hook-provided transcript path. */
public enum TranscriptPathStatus {
    VALID,
    EMPTY,
    MISSING,
    UNREADABLE,
    OUTSIDE_ROOT_OR_SYMLINK
}
