package dev.tracelens.application.tracequery;

import dev.tracelens.domain.tracequery.TraceAnalysisRepository.HookEventEvidence;
import dev.tracelens.domain.tracequery.TraceAnalysisRepository.PerformanceAlignmentEvidence;
import dev.tracelens.domain.tracequery.TraceAnalysisRepository.PerformanceSpanEvidence;
import dev.tracelens.domain.tracequery.TraceAnalysisRepository.TraceTool;
import dev.tracelens.domain.tracequery.TraceAnalysisRepository.TraceTurn;
import dev.tracelens.domain.transcriptcontent.TranscriptSupplementEvidence;

import java.util.List;

/** Stable application result for the Trace detail HTTP adapter. */
public record TraceAnalysisResult(
        TraceTurn session,
        List<HookEventEvidence> agentEvents,
        List<TraceTool> toolCalls,
        List<TranscriptSupplementEvidence> jsonlSupplements,
        List<PerformanceSpanEvidence> performanceSpans,
        List<PerformanceAlignmentEvidence> alignments,
        List<TraceDiagnosis> diagnoses,
        TraceAggregates aggregates) {

    public record TraceDiagnosis(
            String id,
            String severity,
            String title,
            String detail,
            long impactMs,
            String confidence,
            String source) { }

    public record TraceAggregates(
            long totalMs,
            long toolMs,
            long modelRequestMs,
            long approvalMs,
            long localMs,
            long unattributedMs,
            String otelStatus,
            String contentStatus,
            int completeness,
            TraceCoverage coverage) { }

    public record TraceCoverage(
            double hook,
            double transcript,
            double content,
            double otel) { }
}
