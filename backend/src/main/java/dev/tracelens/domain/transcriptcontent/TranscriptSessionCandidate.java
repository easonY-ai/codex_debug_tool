package dev.tracelens.domain.transcriptcontent;

/** Existing Hook session whose transcript path may provide content evidence. */
public record TranscriptSessionCandidate(String sessionId, String transcriptPath) { }
