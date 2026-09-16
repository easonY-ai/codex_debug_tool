package dev.tracelens.domain.transcriptcontent;

import java.util.List;

/** Persistence boundary for file-level transcript bindings. */
public interface TranscriptBindingRepository {
    void save(TranscriptBinding binding);
    List<TranscriptBinding> findAll();
}
