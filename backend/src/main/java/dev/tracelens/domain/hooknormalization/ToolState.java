package dev.tracelens.domain.hooknormalization;

/** Lifecycle state of one Hook tool invocation identified by tool_use_id. */
public enum ToolState {
    UNKNOWN, RUNNING, SUCCESS, FAILED, INTERRUPTED;

    boolean terminal() { return this == SUCCESS || this == FAILED || this == INTERRUPTED; }
}
