package dev.tracelens.domain.hooknormalization;

/** Lifecycle state of one user turn. Terminal states never regress. */
public enum TurnState {
    UNKNOWN, RUNNING, COMPLETED, INTERRUPTED;

    boolean terminal() { return this == COMPLETED || this == INTERRUPTED; }
}
