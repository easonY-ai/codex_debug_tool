package dev.tracelens.infrastructure.transcriptcontent;

import dev.tracelens.domain.transcriptcontent.HookTranscriptTargetRepository;
import dev.tracelens.domain.transcriptcontent.TranscriptSessionCandidate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** MyBatis read adapter for Hook nodes eligible for transcript supplements. */
@Repository
public class MyBatisHookTranscriptTargetRepository implements HookTranscriptTargetRepository {
    private final TranscriptContentMapper transcriptContentMapper;

    public MyBatisHookTranscriptTargetRepository(TranscriptContentMapper transcriptContentMapper) {
        this.transcriptContentMapper = transcriptContentMapper;
    }

    @Override
    public List<TranscriptSessionCandidate> findSessionsWithTranscript() {
        return transcriptContentMapper.findSessionsWithTranscript();
    }

    @Override
    public boolean turnExists(String sessionId, String turnId) {
        return transcriptContentMapper.turnExists(sessionId, turnId);
    }

    @Override
    public boolean toolExists(String sessionId, String turnId, String toolUseId) {
        return transcriptContentMapper.toolExists(sessionId, turnId, toolUseId);
    }
}
