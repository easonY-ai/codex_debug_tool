package dev.tracelens.domain.hooknormalization;

/** Lifecycle state of a Hook session aggregate. */
public enum SessionState {
    UNKNOWN, RUNNING, COMPLETED, INTERRUPTED;

    boolean terminal() { return this == COMPLETED || this == INTERRUPTED; }
}
