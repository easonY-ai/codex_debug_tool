package dev.tracelens.persistence;

public record RawRecord(Long id, long sourceId, int generation, long byteOffset, long endOffset,
                        String contentHash, byte[] rawBytes, String rawText, String parseStatus,
                        String eventType, Long eventTime, long ingestedAt) { }
