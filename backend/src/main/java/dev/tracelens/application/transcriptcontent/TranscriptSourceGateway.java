package dev.tracelens.application.transcriptcontent;

/** File-system and raw-ingestion port used by the transcript content use case. */
public interface TranscriptSourceGateway {
    boolean enabled();
    void refresh();
    TranscriptSourceResolution resolve(String configuredPath);
}
