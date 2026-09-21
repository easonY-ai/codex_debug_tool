package dev.tracelens.domain.execution;

public enum TurnState {
    UNKNOWN, RUNNING, COMPLETED, INTERRUPTED;

    public boolean terminal() {
        return this == COMPLETED || this == INTERRUPTED;
    }
}
