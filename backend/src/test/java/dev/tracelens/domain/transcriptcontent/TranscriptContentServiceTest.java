package dev.tracelens.domain.transcriptcontent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TranscriptContentServiceTest {
    private final HookTranscriptTargetRepository hookTranscriptTargetRepository =
            mock(HookTranscriptTargetRepository.class);
    private final JsonlSupplementRepository jsonlSupplementRepository =
            mock(JsonlSupplementRepository.class);
    private final TranscriptContentService service = new TranscriptContentService(
            hookTranscriptTargetRepository,
            jsonlSupplementRepository);

    @Test
    void exactToolIdentityTargetsTheExistingHookTool() {
        when(hookTranscriptTargetRepository.turnExists("session-demo", "turn-demo")).thenReturn(true);
        when(hookTranscriptTargetRepository.toolExists(
                "session-demo", "turn-demo", "tool-demo")).thenReturn(true);

        service.attachAll("session-demo", List.of(new ParsedTranscriptContent(
                7,
                "turn-demo",
                "tool-demo",
                TranscriptContentKind.TOOL_INPUT,
                "printf demo",
                "codex-2026-09")));

        verify(jsonlSupplementRepository).save(argThat(supplement ->
                supplement.hookNodeType().equals("TOOL")
                        && supplement.hookNodeId().equals("tool-demo")
                        && supplement.mappingLevel() == TranscriptMappingLevel.EXACT));
    }

    @Test
    void unverifiedToolIdentityStaysBoundedToTheTurn() {
        when(hookTranscriptTargetRepository.turnExists("session-demo", "turn-demo")).thenReturn(true);
        when(hookTranscriptTargetRepository.toolExists(
                "session-demo", "turn-demo", "jsonl-call")).thenReturn(false);

        service.attachAll("session-demo", List.of(new ParsedTranscriptContent(
                8,
                "turn-demo",
                "jsonl-call",
                TranscriptContentKind.TOOL_OUTPUT,
                "demo",
                "codex-2026-09")));

        verify(jsonlSupplementRepository).save(argThat(supplement ->
                supplement.hookNodeType().equals("TURN")
                        && supplement.hookNodeId().equals("turn-demo")
                        && supplement.mappingLevel() == TranscriptMappingLevel.BOUNDED));
    }

    @Test
    void transcriptCannotCreateAMissingHookTurn() {
        when(hookTranscriptTargetRepository.turnExists("session-demo", "missing-turn")).thenReturn(false);

        service.attachAll("session-demo", List.of(new ParsedTranscriptContent(
                9,
                "missing-turn",
                null,
                TranscriptContentKind.MODEL_OUTPUT,
                "demo",
                "codex-2026-09")));

        verify(jsonlSupplementRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sameJsonlCallUsesItsOnlyExistingHookTurnAsTheBoundedTarget() {
        when(hookTranscriptTargetRepository.turnExists("session-demo", "jsonl-internal-turn"))
                .thenReturn(false);
        when(hookTranscriptTargetRepository.turnExists("session-demo", "hook-turn"))
                .thenReturn(true);
        when(hookTranscriptTargetRepository.toolExists(
                "session-demo", "hook-turn", "jsonl-call")).thenReturn(false);

        service.attachAll("session-demo", List.of(
                new ParsedTranscriptContent(
                        10,
                        "jsonl-internal-turn",
                        "jsonl-call",
                        TranscriptContentKind.TOOL_INPUT,
                        "printf demo",
                        "codex-2026-09"),
                new ParsedTranscriptContent(
                        11,
                        "hook-turn",
                        "jsonl-call",
                        TranscriptContentKind.TOOL_OUTPUT,
                        "demo",
                        "codex-2026-09")));

        verify(jsonlSupplementRepository).save(argThat(supplement ->
                supplement.rawRecordId() == 10
                        && supplement.hookNodeType().equals("TURN")
                        && supplement.hookNodeId().equals("hook-turn")
                        && supplement.mappingLevel() == TranscriptMappingLevel.BOUNDED));
        verify(jsonlSupplementRepository).save(argThat(supplement ->
                supplement.rawRecordId() == 11
                        && supplement.hookNodeId().equals("hook-turn")
                        && supplement.mappingLevel() == TranscriptMappingLevel.BOUNDED));
    }

    @Test
    void multipleExistingHookTurnsLeaveTheJsonlCallUnattached() {
        when(hookTranscriptTargetRepository.turnExists("session-demo", "hook-turn-a"))
                .thenReturn(true);
        when(hookTranscriptTargetRepository.turnExists("session-demo", "hook-turn-b"))
                .thenReturn(true);

        service.attachAll("session-demo", List.of(
                new ParsedTranscriptContent(
                        12, "hook-turn-a", "ambiguous-call", TranscriptContentKind.TOOL_INPUT,
                        "input", "codex-2026-09"),
                new ParsedTranscriptContent(
                        13, "hook-turn-b", "ambiguous-call", TranscriptContentKind.TOOL_OUTPUT,
                        "output", "codex-2026-09")));

        verify(jsonlSupplementRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
