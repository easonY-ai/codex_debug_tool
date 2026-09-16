package dev.tracelens.domain.tracequery;

import java.util.List;

/** Read boundary for one Hook-backed Turn and its performance evidence. */
public interface TraceAnalysisRepository {
    TraceTurn findTurn(String turnId);
    List<TraceTool> findTools(String turnId);
    List<HookEventEvidence> findHookEvents(String turnId);
    List<PerformanceSpanEvidence> findPerformanceSpans(String turnId);
    List<PerformanceAlignmentEvidence> findAlignments(String turnId);

    record TraceTurn(
            String sessionId,
            String turnId,
            Long startedAt,
            Long endedAt,
            String state,
            String sessionState,
            String transcriptPathStatus,
            String sessionCheckStatus,
            long toolCount,
            long otelToolCount) { }

    record TraceTool(
            String sessionId,
            String turnId,
            String toolUseId,
            String toolName,
            Long preObservedAt,
            Long postObservedAt,
            String state,
            Long estimatedDurationMs,
            int durationValid,
            int version) { }

    record HookEventEvidence(
            long id,
            String deliveryId,
            long observedAt,
            String rawJson,
            String parseStatus) { }

    record PerformanceSpanEvidence(
            long id,
            String signalType,
            String traceId,
            String spanId,
            String turnId,
            String callId,
            String objectKind,
            Long durationMs,
            Long eventTime,
            long receivedAt,
            String rawJson,
            String parseStatus) { }

    record PerformanceAlignmentEvidence(
            long id,
            String hookNodeType,
            String hookNodeId,
            long otelObjectId,
            String level,
            String evidenceJson,
            String algorithmVersion,
            Long timeDeltaMs) { }
}
