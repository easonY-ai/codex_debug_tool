package dev.tracelens.domain.transcriptcontent;

/** User-visible content that a transcript may add to an existing Hook node. */
public enum TranscriptContentKind {
    USER_INPUT,
    MODEL_OUTPUT,
    REASONING_SUMMARY,
    TOOL_INPUT,
    TOOL_OUTPUT;

    public boolean belongsToTool() {
        return this == TOOL_INPUT || this == TOOL_OUTPUT;
    }
}
