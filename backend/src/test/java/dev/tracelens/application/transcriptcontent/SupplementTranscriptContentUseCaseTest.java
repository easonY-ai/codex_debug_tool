package dev.tracelens.application.transcriptcontent;

import dev.tracelens.domain.transcriptcontent.HookTranscriptTargetRepository;
import dev.tracelens.domain.transcriptcontent.ParsedTranscriptContent;
import dev.tracelens.domain.transcriptcontent.RawTranscriptRecord;
import dev.tracelens.domain.transcriptcontent.TranscriptBindingRepository;
import dev.tracelens.domain.transcriptcontent.TranscriptContentKind;
import dev.tracelens.domain.transcriptcontent.TranscriptContentService;
import dev.tracelens.domain.transcriptcontent.TranscriptPathStatus;
import dev.tracelens.domain.transcriptcontent.TranscriptSessionCandidate;
import dev.tracelens.domain.transcriptcontent.UnknownTranscriptEvidenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupplementTranscriptContentUseCaseTest {
    @Test
    void sessionMismatchPersistsEvidenceButNeverAttachesContent() {
        Fixture fixture = new Fixture();
        when(fixture.parser.parse(fixture.meta))
                .thenReturn(TranscriptRecordParseResult.sessionMeta("another-session"));

        fixture.useCase.processAvailable();

        verify(fixture.transcriptBindingRepository).save(any());
        verify(fixture.transcriptContentService, never()).attachAll(any(), any());
    }

    @Test
    void matchedSessionAttachesStructuredContent() {
        Fixture fixture = new Fixture();
        ParsedTranscriptContent content = new ParsedTranscriptContent(
                fixture.content.id(),
                "turn-demo",
                null,
                TranscriptContentKind.USER_INPUT,
                "Synthetic question",
                "codex-2026-09");
        when(fixture.parser.parse(fixture.meta))
                .thenReturn(TranscriptRecordParseResult.sessionMeta("session-demo"));
        when(fixture.parser.parse(fixture.content))
                .thenReturn(TranscriptRecordParseResult.content(content));

        fixture.useCase.processAvailable();

        verify(fixture.transcriptContentService).attachAll("session-demo", List.of(content));
    }

    private static final class Fixture {
        final TranscriptSourceGateway sourceGateway = mock(TranscriptSourceGateway.class);
        final HookTranscriptTargetRepository hookTranscriptTargetRepository =
                mock(HookTranscriptTargetRepository.class);
        final TranscriptBindingRepository transcriptBindingRepository =
                mock(TranscriptBindingRepository.class);
        final TranscriptContentService transcriptContentService = mock(TranscriptContentService.class);
        final UnknownTranscriptEvidenceRepository unknownTranscriptEvidenceRepository =
                mock(UnknownTranscriptEvidenceRepository.class);
        final TranscriptRecordParser parser = mock(TranscriptRecordParser.class);
        final TransactionTemplate transactions = mock(TransactionTemplate.class);
        final RawTranscriptRecord meta = new RawTranscriptRecord(1, "VALID_JSON", "{}");
        final RawTranscriptRecord content = new RawTranscriptRecord(2, "VALID_JSON", "{}");
        final SupplementTranscriptContentUseCase useCase;

        Fixture() {
            when(sourceGateway.enabled()).thenReturn(true);
            when(hookTranscriptTargetRepository.findSessionsWithTranscript()).thenReturn(List.of(
                    new TranscriptSessionCandidate("session-demo", "/workspace/demo.jsonl")));
            when(sourceGateway.resolve("/workspace/demo.jsonl")).thenReturn(
                    new TranscriptSourceResolution(
                            TranscriptPathStatus.VALID,
                            "/workspace/demo.jsonl",
                            7L,
                            List.of(meta, content)));
            when(parser.adapterVersion()).thenReturn("codex-2026-09");
            executeCallbacksImmediately(transactions);
            useCase = new SupplementTranscriptContentUseCase(
                    sourceGateway,
                    hookTranscriptTargetRepository,
                    transcriptBindingRepository,
                    transcriptContentService,
                    unknownTranscriptEvidenceRepository,
                    parser,
                    transactions,
                    Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC));
        }
    }

    @SuppressWarnings("unchecked")
    private static void executeCallbacksImmediately(TransactionTemplate transactions) {
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
    }
}
