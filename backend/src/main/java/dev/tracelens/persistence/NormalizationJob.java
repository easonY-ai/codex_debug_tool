package dev.tracelens.persistence;

public record NormalizationJob(long id, String sourceKind, long sourceId, String status,
                               int attempts, long availableAt, long createdAt, long updatedAt,
                               String errorCode) { }
