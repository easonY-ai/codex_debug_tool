package dev.tracelens.persistence;

public record SourceFile(long id, String path, String fileKey, int generation, long byteOffset,
                         String anchorHash, long observedSize, long modifiedAt, long scannedAt) { }
