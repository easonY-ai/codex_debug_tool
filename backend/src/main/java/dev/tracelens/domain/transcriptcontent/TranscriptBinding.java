package dev.tracelens.domain.transcriptcontent;

/**
 * File-level evidence binding one Hook session to one transcript source.
 *
 * <p>Content may be attached only when the path is safe and session_meta proves that the
 * transcript belongs to the same Hook session.</p>
 */
public record TranscriptBinding(
        String sessionId,
        String configuredPath,
        String canonicalPath,
        TranscriptPathStatus pathStatus,
        Long sourceFileId,
        Long sessionMetaRecordId,
        String jsonlSessionId,
        TranscriptSessionCheckStatus sessionCheckStatus,
        String adapterVersion,
        long checkedAt) {

    public TranscriptBinding {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        if (pathStatus == null || sessionCheckStatus == null) {
            throw new IllegalArgumentException("binding statuses are required");
        }
    }

    public static TranscriptBinding pathFailure(
            String sessionId,
            String configuredPath,
            TranscriptPathStatus pathStatus,
            long checkedAt) {
        if (pathStatus == TranscriptPathStatus.VALID) {
            throw new IllegalArgumentException("A valid path requires a session check");
        }
        return new TranscriptBinding(
                sessionId,
                configuredPath,
                null,
                pathStatus,
                null,
                null,
                null,
                TranscriptSessionCheckStatus.NOT_CHECKED,
                null,
                checkedAt);
    }

    public static TranscriptBinding checked(
            String sessionId,
            String configuredPath,
            String canonicalPath,
            Long sourceFileId,
            Long sessionMetaRecordId,
            String jsonlSessionId,
            TranscriptSessionCheckStatus sessionCheckStatus,
            String adapterVersion,
            long checkedAt) {
        return new TranscriptBinding(
                sessionId,
                configuredPath,
                canonicalPath,
                TranscriptPathStatus.VALID,
                sourceFileId,
                sessionMetaRecordId,
                jsonlSessionId,
                sessionCheckStatus,
                adapterVersion,
                checkedAt);
    }

    public boolean allowsContentSupplement() {
        return pathStatus == TranscriptPathStatus.VALID
                && sessionCheckStatus == TranscriptSessionCheckStatus.MATCHED;
    }
}
