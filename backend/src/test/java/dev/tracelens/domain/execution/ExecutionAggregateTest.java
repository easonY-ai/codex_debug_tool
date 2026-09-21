package dev.tracelens.domain.execution;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionAggregateTest {
    @Test
    void terminalTurnDoesNotRegressWhenLatePromptArrives() {
        CodexTurn turn = CodexTurn.empty("session-demo", "turn-demo")
                .applyTurnFact(fact(HookLifecycleFact.prompt(1000)))
                .applyTurnFact(fact(HookLifecycleFact.stop(2000)))
                .applyTurnFact(fact(HookLifecycleFact.prompt(3000)));

        assertThat(turn.state()).isEqualTo(TurnState.COMPLETED);
        assertThat(turn.startedAt()).isEqualTo(1000);
        assertThat(turn.endedAt()).isEqualTo(2000);
    }

    @Test
    void turnRetainsStructuredPromptModelAndWorkingDirectory() {
        NormalizedHookFact fact = new NormalizedHookFact(
                "UserPromptSubmit", "session-demo", null, "turn-demo", null,
                "Inspect tests", "gpt-demo", "/workspace/demo-project", HookLifecycleFact.prompt(1000));

        CodexTurn turn = CodexTurn.empty("session-demo", "turn-demo").applyTurnFact(fact);

        assertThat(turn.title()).isEqualTo("Inspect tests");
        assertThat(turn.model()).isEqualTo("gpt-demo");
        assertThat(turn.workingDirectory()).isEqualTo("/workspace/demo-project");
    }

    @Test
    void postBeforePreProducesSuccessfulToolCallWithValidDuration() {
        ToolCall toolCall = ToolCall.empty("session-demo", "turn-demo", "tool-demo")
                .applyToolFact(fact(HookLifecycleFact.postTool(2000, "Bash", ToolCallState.SUCCESS)))
                .applyToolFact(fact(HookLifecycleFact.preTool(1000, "Bash")));

        assertThat(toolCall.state()).isEqualTo(ToolCallState.SUCCESS);
        assertThat(toolCall.durationValid()).isTrue();
        assertThat(toolCall.estimatedDurationMs()).isEqualTo(1000);
    }

    @Test
    void reverseToolBoundariesDoNotProduceDuration() {
        ToolCall toolCall = ToolCall.empty("session-demo", "turn-demo", "tool-demo")
                .applyToolFact(fact(HookLifecycleFact.postTool(1000, "Bash", ToolCallState.SUCCESS)))
                .applyToolFact(fact(HookLifecycleFact.preTool(2000, "Bash")));

        assertThat(toolCall.durationValid()).isFalse();
        assertThat(toolCall.estimatedDurationMs()).isNull();
    }

    @Test
    void equalTurnIdsInDifferentSessionsRemainDifferentBusinessIdentities() {
        CodexTurn first = CodexTurn.empty("session-a", "shared-turn");
        CodexTurn second = CodexTurn.empty("session-b", "shared-turn");

        assertThat(first.businessIdentity()).isNotEqualTo(second.businessIdentity());
    }

    private static NormalizedHookFact fact(HookLifecycleFact lifecycleFact) {
        return new NormalizedHookFact(
                lifecycleFact.name(), "session-demo", null, "turn-demo", "tool-demo",
                null, null, null, lifecycleFact);
    }
}
