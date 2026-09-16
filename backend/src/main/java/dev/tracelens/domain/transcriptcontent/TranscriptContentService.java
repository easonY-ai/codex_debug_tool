package dev.tracelens.domain.transcriptcontent;

import dev.tracelens.domain.operationaldiagnostics.AuditedBusinessOperations;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Domain service that owns the complete Hook-target selection and idempotent supplement save.
 */
@AuditedBusinessOperations
public class TranscriptContentService {
    private final HookTranscriptTargetRepository hookTranscriptTargetRepository;
    private final JsonlSupplementRepository jsonlSupplementRepository;

    public TranscriptContentService(
            HookTranscriptTargetRepository hookTranscriptTargetRepository,
            JsonlSupplementRepository jsonlSupplementRepository) {
        this.hookTranscriptTargetRepository = hookTranscriptTargetRepository;
        this.jsonlSupplementRepository = jsonlSupplementRepository;
    }

    public int attachAll(String sessionId, List<ParsedTranscriptContent> contents) {
        Map<String, Boolean> existingTurns = existingTurns(sessionId, contents);
        Map<String, Set<String>> hookTurnCandidatesByCallId = new HashMap<>();
        for (ParsedTranscriptContent content : contents) {
            if (content.kind().belongsToTool()
                    && content.callId() != null
                    && Boolean.TRUE.equals(existingTurns.get(content.turnId()))) {
                hookTurnCandidatesByCallId
                        .computeIfAbsent(content.callId(), ignored -> new HashSet<>())
                        .add(content.turnId());
            }
        }

        int attached = 0;
        for (ParsedTranscriptContent content : contents) {
            String targetTurnId = targetTurnId(
                    content,
                    existingTurns,
                    hookTurnCandidatesByCallId);
            if (targetTurnId != null) {
                save(sessionId, targetTurnId, content);
                attached++;
            }
        }
        return attached;
    }

    private Map<String, Boolean> existingTurns(
            String sessionId,
            List<ParsedTranscriptContent> contents) {
        Map<String, Boolean> existingTurns = new HashMap<>();
        for (ParsedTranscriptContent content : contents) {
            existingTurns.computeIfAbsent(
                    content.turnId(),
                    turnId -> hookTranscriptTargetRepository.turnExists(sessionId, turnId));
        }
        return existingTurns;
    }

    private static String targetTurnId(
            ParsedTranscriptContent content,
            Map<String, Boolean> existingTurns,
            Map<String, Set<String>> hookTurnCandidatesByCallId) {
        if (!content.kind().belongsToTool() || content.callId() == null) {
            return Boolean.TRUE.equals(existingTurns.get(content.turnId()))
                    ? content.turnId()
                    : null;
        }
        Set<String> candidates = hookTurnCandidatesByCallId.getOrDefault(
                content.callId(),
                Set.of());
        return candidates.size() == 1 ? candidates.iterator().next() : null;
    }

    private void save(
            String sessionId,
            String targetTurnId,
            ParsedTranscriptContent content) {
        boolean exactTool = content.kind().belongsToTool()
                && content.callId() != null
                && hookTranscriptTargetRepository.toolExists(
                        sessionId,
                        targetTurnId,
                        content.callId());
        String nodeType = exactTool ? "TOOL" : "TURN";
        String nodeId = exactTool ? content.callId() : targetTurnId;
        TranscriptMappingLevel mappingLevel = exactTool
                ? TranscriptMappingLevel.EXACT
                : TranscriptMappingLevel.BOUNDED;

        jsonlSupplementRepository.save(new TranscriptContentSupplement(
                nodeType,
                nodeId,
                content.rawRecordId(),
                content.kind(),
                content.callId(),
                mappingLevel,
                content.adapterVersion(),
                content.text()));
    }
}
