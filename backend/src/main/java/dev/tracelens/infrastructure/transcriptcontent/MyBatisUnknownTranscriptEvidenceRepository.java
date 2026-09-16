package dev.tracelens.infrastructure.transcriptcontent;

import dev.tracelens.domain.transcriptcontent.UnknownTranscriptEvidenceRepository;
import dev.tracelens.persistence.IngestionMapper;
import org.springframework.stereotype.Repository;

/** Adapter retaining UNKNOWN structure fingerprints while S2 handles known content separately. */
@Repository
public class MyBatisUnknownTranscriptEvidenceRepository implements UnknownTranscriptEvidenceRepository {
    private final IngestionMapper ingestionMapper;

    public MyBatisUnknownTranscriptEvidenceRepository(IngestionMapper ingestionMapper) {
        this.ingestionMapper = ingestionMapper;
    }

    @Override
    public void register(long rawRecordId, String canonicalShape, long observedAt) {
        String sha256 = JacksonTranscriptRecordParser.shapeSha256(canonicalShape);
        ingestionMapper.upsertUnknownFingerprint(sha256, canonicalShape, observedAt);
        Long fingerprintId = ingestionMapper.unknownFingerprintId(sha256);
        if (fingerprintId != null
                && ingestionMapper.linkUnknownRecord(fingerprintId, rawRecordId) == 1) {
            ingestionMapper.incrementUnknownFingerprint(fingerprintId, observedAt);
        }
    }
}
