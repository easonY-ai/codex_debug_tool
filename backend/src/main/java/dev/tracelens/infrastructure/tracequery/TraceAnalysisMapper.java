package dev.tracelens.infrastructure.tracequery;

import dev.tracelens.domain.tracequery.TraceAnalysisRepository.HookEventEvidence;
import dev.tracelens.domain.tracequery.TraceAnalysisRepository.PerformanceAlignmentEvidence;
import dev.tracelens.domain.tracequery.TraceAnalysisRepository.PerformanceSpanEvidence;
import dev.tracelens.domain.tracequery.TraceAnalysisRepository.TraceTool;
import dev.tracelens.domain.tracequery.TraceAnalysisRepository.TraceTurn;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/** Simple read statements for the Trace analysis Repository adapter. */
@Mapper
public interface TraceAnalysisMapper {
    TraceTurn findTurn(String turnId);
    List<TraceTool> findTools(String turnId);
    List<HookEventEvidence> findHookEvents(String turnId);
    List<PerformanceSpanEvidence> findPerformanceSpans(String turnId);
    List<PerformanceAlignmentEvidence> findAlignments(String turnId);
}
