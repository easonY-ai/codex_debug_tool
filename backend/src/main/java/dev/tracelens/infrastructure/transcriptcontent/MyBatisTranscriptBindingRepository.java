package dev.tracelens.infrastructure.transcriptcontent;

import dev.tracelens.domain.transcriptcontent.TranscriptBinding;
import dev.tracelens.domain.transcriptcontent.TranscriptBindingRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** MyBatis Repository for file-level transcript bindings. */
@Repository
public class MyBatisTranscriptBindingRepository implements TranscriptBindingRepository {
    private final TranscriptContentMapper transcriptContentMapper;

    public MyBatisTranscriptBindingRepository(TranscriptContentMapper transcriptContentMapper) {
        this.transcriptContentMapper = transcriptContentMapper;
    }

    @Override
    public void save(TranscriptBinding binding) {
        transcriptContentMapper.upsertBinding(binding);
    }

    @Override
    public List<TranscriptBinding> findAll() {
        return transcriptContentMapper.findBindings();
    }
}
