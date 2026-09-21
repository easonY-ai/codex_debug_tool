package dev.tracelens.domain.execution;

public enum SessionState {
    UNKNOWN, RUNNING, COMPLETED, INTERRUPTED;

    public boolean terminal() {
        return this == COMPLETED || this == INTERRUPTED;
    }
}
