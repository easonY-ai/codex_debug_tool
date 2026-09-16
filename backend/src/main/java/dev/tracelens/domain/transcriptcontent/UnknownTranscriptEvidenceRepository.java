package dev.tracelens.domain.transcriptcontent;

/** Preserves the existing UNKNOWN fingerprint behavior without mixing it into content rules. */
public interface UnknownTranscriptEvidenceRepository {
    void register(long rawRecordId, String canonicalShape, long observedAt);
}
