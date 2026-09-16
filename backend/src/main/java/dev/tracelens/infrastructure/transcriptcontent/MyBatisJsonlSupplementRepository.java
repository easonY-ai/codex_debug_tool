package dev.tracelens.infrastructure.transcriptcontent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tracelens.domain.transcriptcontent.JsonlSupplementRepository;
import dev.tracelens.domain.transcriptcontent.TranscriptContentSupplement;
import dev.tracelens.domain.transcriptcontent.TranscriptMappingLevel;
import dev.tracelens.domain.transcriptcontent.TranscriptSupplementEvidence;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** MyBatis Repository for normalized JSONL content evidence. */
@Repository
public class MyBatisJsonlSupplementRepository implements JsonlSupplementRepository {
    private final TranscriptContentMapper transcriptContentMapper;
    private final ObjectMapper objectMapper;

    public MyBatisJsonlSupplementRepository(
            TranscriptContentMapper transcriptContentMapper,
            ObjectMapper objectMapper) {
        this.transcriptContentMapper = transcriptContentMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(TranscriptContentSupplement supplement) {
        transcriptContentMapper.insertSupplement(supplement, evidenceJson(supplement));
    }

    @Override
    public List<TranscriptSupplementEvidence> findForTurn(String turnId) {
        return transcriptContentMapper.findSupplementsForTurn(turnId);
    }

    private String evidenceJson(TranscriptContentSupplement supplement) {
        String field = supplement.mappingLevel() == TranscriptMappingLevel.EXACT
                ? "hook.tool_use_id/jsonl.call_id"
                : "hook.turn_id/jsonl.turn_id";
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "adapter", supplement.adapterVersion(),
                    "field", field));
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Unable to serialize controlled transcript evidence", impossible);
        }
    }
}
