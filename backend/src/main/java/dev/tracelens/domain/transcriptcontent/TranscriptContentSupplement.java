package dev.tracelens.domain.transcriptcontent;

/** Idempotent content evidence attached to one existing Hook Turn or Tool node. */
public record TranscriptContentSupplement(
        String hookNodeType,
        String hookNodeId,
        long rawRecordId,
        TranscriptContentKind contentKind,
        String callId,
        TranscriptMappingLevel mappingLevel,
        String adapterVersion,
        String contentText) { }
