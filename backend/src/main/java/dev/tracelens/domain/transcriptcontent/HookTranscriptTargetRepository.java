package dev.tracelens.domain.transcriptcontent;

import java.util.List;

/** Read boundary for Hook nodes that are eligible to receive transcript content. */
public interface HookTranscriptTargetRepository {
    List<TranscriptSessionCandidate> findSessionsWithTranscript();
    boolean turnExists(String sessionId, String turnId);
    boolean toolExists(String sessionId, String turnId, String toolUseId);
}
