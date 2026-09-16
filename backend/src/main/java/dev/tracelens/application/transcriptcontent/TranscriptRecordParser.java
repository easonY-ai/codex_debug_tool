package dev.tracelens.application.transcriptcontent;

import dev.tracelens.domain.transcriptcontent.RawTranscriptRecord;

/** Versioned adapter boundary for unstable Codex transcript JSON. */
public interface TranscriptRecordParser {
    String adapterVersion();
    TranscriptRecordParseResult parse(RawTranscriptRecord record);
}
