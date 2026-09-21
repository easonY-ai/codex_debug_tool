package dev.tracelens.infrastructure.execution;

import dev.tracelens.domain.execution.CodexTurn;
import dev.tracelens.domain.execution.HookLifecycleFact;
import dev.tracelens.domain.execution.NormalizedHookFact;
import dev.tracelens.persistence.IngestionMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionRepositoryTest {
    @Test
    void turnLookupUsesTheCompleteBusinessIdentity() {
        IngestionMapper ingestionMapper = mock(IngestionMapper.class);
        when(ingestionMapper.executionTurn("session-a", "shared-turn")).thenReturn(null);
        when(ingestionMapper.executionTurn("session-b", "shared-turn")).thenReturn(Map.of(
                "state", "RUNNING",
                "version", 3));
        MyBatisCodexTurnRepository repository = new MyBatisCodexTurnRepository(ingestionMapper);

        CodexTurn first = repository.findTurn("session-a", "shared-turn");
        CodexTurn second = repository.findTurn("session-b", "shared-turn");

        assertThat(first.revision()).isZero();
        assertThat(second.revision()).isEqualTo(3);
        assertThat(first.businessIdentity()).isNotEqualTo(second.businessIdentity());
        verify(ingestionMapper).executionTurn("session-a", "shared-turn");
        verify(ingestionMapper).executionTurn("session-b", "shared-turn");
    }

    @Test
    void recordingTurnStateAppendsTheSameRevisionToTheExecutionChangeFeed() {
        IngestionMapper ingestionMapper = mock(IngestionMapper.class);
        when(ingestionMapper.executionTurn("session-a", "turn-a")).thenReturn(null);
        MyBatisCodexTurnRepository repository = new MyBatisCodexTurnRepository(ingestionMapper);
        NormalizedHookFact fact = new NormalizedHookFact(
                "UserPromptSubmit", "session-a", null, "turn-a", null,
                "Demo", "gpt-demo", "/workspace/demo-project", HookLifecycleFact.prompt(1234));
        CodexTurn turn = CodexTurn.empty("session-a", "turn-a").applyTurnFact(fact);

        repository.recordTurnState(turn);

        verify(ingestionMapper).insertExecutionTurn(turn);
        verify(ingestionMapper).appendExecutionChange(
                "TURN", "session-a", "turn-a", null, turn.revision(), 1234L);
    }
}
