package dev.tracelens.application.transcriptcontent;

import dev.tracelens.domain.operationaldiagnostics.AuditedBusinessOperations;
import dev.tracelens.domain.transcriptcontent.HookTranscriptTargetRepository;
import dev.tracelens.domain.transcriptcontent.ParsedTranscriptContent;
import dev.tracelens.domain.transcriptcontent.RawTranscriptRecord;
import dev.tracelens.domain.transcriptcontent.TranscriptBinding;
import dev.tracelens.domain.transcriptcontent.TranscriptBindingRepository;
import dev.tracelens.domain.transcriptcontent.TranscriptContentService;
import dev.tracelens.domain.transcriptcontent.TranscriptPathStatus;
import dev.tracelens.domain.transcriptcontent.TranscriptSessionCandidate;
import dev.tracelens.domain.transcriptcontent.TranscriptSessionCheckStatus;
import dev.tracelens.domain.transcriptcontent.UnknownTranscriptEvidenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Application use case for refreshing safe transcript bindings and attaching visible content.
 * File IO and JSON stay behind ports; each persisted decision uses a short transaction.
 */
@Service
@AuditedBusinessOperations
public class SupplementTranscriptContentUseCase {
    private final TranscriptSourceGateway transcriptSourceGateway;
    private final HookTranscriptTargetRepository hookTranscriptTargetRepository;
    private final TranscriptBindingRepository transcriptBindingRepository;
    private final TranscriptContentService transcriptContentService;
    private final UnknownTranscriptEvidenceRepository unknownTranscriptEvidenceRepository;
    private final TranscriptRecordParser transcriptRecordParser;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Autowired
    public SupplementTranscriptContentUseCase(
            TranscriptSourceGateway transcriptSourceGateway,
            HookTranscriptTargetRepository hookTranscriptTargetRepository,
            TranscriptBindingRepository transcriptBindingRepository,
            TranscriptContentService transcriptContentService,
            UnknownTranscriptEvidenceRepository unknownTranscriptEvidenceRepository,
            TranscriptRecordParser transcriptRecordParser,
            TransactionTemplate transactions) {
        this(
                transcriptSourceGateway,
                hookTranscriptTargetRepository,
                transcriptBindingRepository,
                transcriptContentService,
                unknownTranscriptEvidenceRepository,
                transcriptRecordParser,
                transactions,
                Clock.systemUTC());
    }

    SupplementTranscriptContentUseCase(
            TranscriptSourceGateway transcriptSourceGateway,
            HookTranscriptTargetRepository hookTranscriptTargetRepository,
            TranscriptBindingRepository transcriptBindingRepository,
            TranscriptContentService transcriptContentService,
            UnknownTranscriptEvidenceRepository unknownTranscriptEvidenceRepository,
            TranscriptRecordParser transcriptRecordParser,
            TransactionTemplate transactions,
            Clock clock) {
        this.transcriptSourceGateway = transcriptSourceGateway;
        this.hookTranscriptTargetRepository = hookTranscriptTargetRepository;
        this.transcriptBindingRepository = transcriptBindingRepository;
        this.transcriptContentService = transcriptContentService;
        this.unknownTranscriptEvidenceRepository = unknownTranscriptEvidenceRepository;
        this.transcriptRecordParser = transcriptRecordParser;
        this.transactions = transactions;
        this.clock = clock;
    }

    public int processAvailable() {
        if (!transcriptSourceGateway.enabled()) {
            return 0;
        }
        // 启动一次文件遍历，并且注册文件系统监听器，后续通过watchEvents方法增量触发扫描，避免频繁轮询。
        transcriptSourceGateway.refresh();
        List<TranscriptSessionCandidate> candidates =
                hookTranscriptTargetRepository.findSessionsWithTranscript();
        for (TranscriptSessionCandidate candidate : candidates) {
            process(candidate);
        }
        return candidates.size();
    }

    private void process(TranscriptSessionCandidate candidate) {
        long checkedAt = clock.millis();
        TranscriptSourceResolution source =
                transcriptSourceGateway.resolve(candidate.transcriptPath());
        if (source.pathStatus() != TranscriptPathStatus.VALID) {
            saveBinding(TranscriptBinding.pathFailure(
                    candidate.sessionId(),
                    candidate.transcriptPath(),
                    source.pathStatus(),
                    checkedAt));
            return;
        }

        if (source.records().isEmpty()) {
            saveBinding(checkedBinding(
                    candidate,
                    source,
                    null,
                    null,
                    TranscriptSessionCheckStatus.SESSION_META_MISSING,
                    checkedAt));
            return;
        }

        RawTranscriptRecord first = source.records().get(0);
        TranscriptRecordParseResult sessionMeta = transcriptRecordParser.parse(first);
        if (sessionMeta.kind() != TranscriptRecordParseResult.Kind.SESSION_META
                || sessionMeta.sessionId() == null) {
            saveBinding(checkedBinding(
                    candidate,
                    source,
                    first.id(),
                    null,
                    TranscriptSessionCheckStatus.SESSION_META_UNSUPPORTED,
                    checkedAt));
            return;
        }

        TranscriptSessionCheckStatus sessionCheckStatus = candidate.sessionId().equals(sessionMeta.sessionId())
                ? TranscriptSessionCheckStatus.MATCHED
                : TranscriptSessionCheckStatus.SESSION_ID_MISMATCH;
        TranscriptBinding binding = checkedBinding(
                candidate,
                source,
                first.id(),
                sessionMeta.sessionId(),
                sessionCheckStatus,
                checkedAt);
        saveBinding(binding);
        if (!binding.allowsContentSupplement()) {
            return;
        }

        List<ParsedTranscriptContent> contents = new ArrayList<>();
        for (RawTranscriptRecord record : source.records().subList(1, source.records().size())) {
            TranscriptRecordParseResult parsed = transcriptRecordParser.parse(record);
            if (parsed.kind() == TranscriptRecordParseResult.Kind.CONTENT) {
                contents.add(parsed.content());
            } else if (parsed.kind() == TranscriptRecordParseResult.Kind.UNKNOWN) {
                registerUnknown(record.id(), parsed.unknownShape(), checkedAt);
            }
        }
        if (!contents.isEmpty()) {
            transactions.executeWithoutResult(ignored ->
                    transcriptContentService.attachAll(candidate.sessionId(), contents));
        }
    }

    private TranscriptBinding checkedBinding(
            TranscriptSessionCandidate candidate,
            TranscriptSourceResolution source,
            Long sessionMetaRecordId,
            String jsonlSessionId,
            TranscriptSessionCheckStatus sessionCheckStatus,
            long checkedAt) {
        return TranscriptBinding.checked(
                candidate.sessionId(),
                candidate.transcriptPath(),
                source.canonicalPath(),
                source.sourceFileId(),
                sessionMetaRecordId,
                jsonlSessionId,
                sessionCheckStatus,
                transcriptRecordParser.adapterVersion(),
                checkedAt);
    }

    private void registerUnknown(long recordId, String unknownShape, long observedAt) {
        transactions.executeWithoutResult(ignored ->
                unknownTranscriptEvidenceRepository.register(
                        recordId,
                        unknownShape,
                        observedAt));
    }

    private void saveBinding(TranscriptBinding binding) {
        transactions.executeWithoutResult(ignored -> transcriptBindingRepository.save(binding));
    }
}
