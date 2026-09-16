package dev.tracelens.application.transcriptcontent;

import dev.tracelens.domain.transcriptcontent.RawTranscriptRecord;
import dev.tracelens.domain.transcriptcontent.TranscriptPathStatus;

import java.util.List;

/** Safe file-system resolution plus already-ingested raw records for one transcript path. */
public record TranscriptSourceResolution(
        TranscriptPathStatus pathStatus,
        String canonicalPath,
        Long sourceFileId,
        List<RawTranscriptRecord> records) {
    public TranscriptSourceResolution {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
