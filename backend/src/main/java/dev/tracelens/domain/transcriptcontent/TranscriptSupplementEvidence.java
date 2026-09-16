package dev.tracelens.domain.transcriptcontent;

/** Query projection containing both normalized content and its immutable raw JSONL evidence. */
public record TranscriptSupplementEvidence(
        long id,
        String hookNodeType,
        String hookNodeId,
        long rawRecordId,
        TranscriptContentKind contentKind,
        String callId,
        TranscriptMappingLevel mappingLevel,
        String adapterVersion,
        String contentText,
        String rawJson) { }
