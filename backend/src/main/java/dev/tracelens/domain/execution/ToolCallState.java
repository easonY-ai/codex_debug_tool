package dev.tracelens.domain.execution;

public enum ToolCallState {
    UNKNOWN, RUNNING, SUCCESS, FAILED, INTERRUPTED;

    public boolean terminal() {
        return this == SUCCESS || this == FAILED || this == INTERRUPTED;
    }
}
