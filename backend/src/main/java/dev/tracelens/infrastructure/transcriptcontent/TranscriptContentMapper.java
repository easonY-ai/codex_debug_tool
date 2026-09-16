package dev.tracelens.infrastructure.transcriptcontent;

import dev.tracelens.domain.transcriptcontent.RawTranscriptRecord;
import dev.tracelens.domain.transcriptcontent.TranscriptBinding;
import dev.tracelens.domain.transcriptcontent.TranscriptContentSupplement;
import dev.tracelens.domain.transcriptcontent.TranscriptSessionCandidate;
import dev.tracelens.domain.transcriptcontent.TranscriptSupplementEvidence;
import dev.tracelens.persistence.SourceFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** Simple MyBatis statements used by transcript-content Repository adapters. */
@Mapper
public interface TranscriptContentMapper {
    List<TranscriptSessionCandidate> findSessionsWithTranscript();
    boolean turnExists(@Param("sessionId") String sessionId, @Param("turnId") String turnId);
    boolean toolExists(
            @Param("sessionId") String sessionId,
            @Param("turnId") String turnId,
            @Param("toolUseId") String toolUseId);
    SourceFile sourceByPath(String path);
    List<RawTranscriptRecord> recordsForSource(long sourceId);
    void upsertBinding(TranscriptBinding binding);
    List<TranscriptBinding> findBindings();
    void insertSupplement(
            @Param("supplement") TranscriptContentSupplement supplement,
            @Param("evidenceJson") String evidenceJson);
    List<TranscriptSupplementEvidence> findSupplementsForTurn(String turnId);
}
