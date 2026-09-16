package dev.tracelens.domain.transcriptcontent;

import java.util.List;

/** Persistence boundary for content attached to Hook nodes. */
public interface JsonlSupplementRepository {
    void save(TranscriptContentSupplement supplement);
    List<TranscriptSupplementEvidence> findForTurn(String turnId);
}
