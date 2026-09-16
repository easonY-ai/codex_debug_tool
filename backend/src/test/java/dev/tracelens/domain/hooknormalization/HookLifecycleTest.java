package dev.tracelens.domain.hooknormalization;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HookLifecycleTest {
    @Test void terminalTurnDoesNotRegressWhenLatePromptArrives() {
        TurnLifecycle turn = TurnLifecycle.empty("session-demo", "turn-demo")
                .evolveFrom(event(HookLifecycleEvent.prompt(1000)))
                .evolveFrom(event(HookLifecycleEvent.stop(2000)))
                .evolveFrom(event(HookLifecycleEvent.prompt(3000)));

        assertThat(turn.state()).isEqualTo(TurnState.COMPLETED);
        assertThat(turn.startedAt()).isEqualTo(1000);
        assertThat(turn.endedAt()).isEqualTo(2000);
    }

    @Test void postBeforePreProducesSuccessfulToolWithValidDurationWhenBothBoundariesArrive() {
        ToolLifecycle tool = ToolLifecycle.empty("session-demo", "turn-demo", "tool-demo")
                .evolveFrom(event(HookLifecycleEvent.postTool(2000, "Bash", ToolState.SUCCESS)))
                .evolveFrom(event(HookLifecycleEvent.preTool(1000, "Bash")));

        assertThat(tool.state()).isEqualTo(ToolState.SUCCESS);
        assertThat(tool.durationValid()).isTrue();
        assertThat(tool.estimatedDurationMs()).isEqualTo(1000);
    }

    @Test void toolWithReverseBoundariesDoesNotProduceDuration() {
        ToolLifecycle tool = ToolLifecycle.empty("session-demo", "turn-demo", "tool-demo")
                .evolveFrom(event(HookLifecycleEvent.postTool(1000, "Bash", ToolState.SUCCESS)))
                .evolveFrom(event(HookLifecycleEvent.preTool(2000, "Bash")));

        assertThat(tool.durationValid()).isFalse();
        assertThat(tool.estimatedDurationMs()).isNull();
    }

    private static NormalizedHookEvent event(HookLifecycleEvent lifecycleEvent) {
        return new NormalizedHookEvent(
                lifecycleEvent.name(), "session-demo", null, "turn-demo", "tool-demo", lifecycleEvent);
    }
}
