package dev.tracelens.domain.transcriptcontent;

/** Immutable raw JSONL evidence already ingested by the checkpoint scanner. */
public record RawTranscriptRecord(long id, String parseStatus, String rawText) { }
