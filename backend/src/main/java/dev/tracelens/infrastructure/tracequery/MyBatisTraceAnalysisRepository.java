package dev.tracelens.infrastructure.tracequery;

import dev.tracelens.domain.tracequery.TraceAnalysisRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** MyBatis implementation of the Trace analysis read boundary. */
@Repository
public class MyBatisTraceAnalysisRepository implements TraceAnalysisRepository {
    private final TraceAnalysisMapper traceAnalysisMapper;

    public MyBatisTraceAnalysisRepository(TraceAnalysisMapper traceAnalysisMapper) {
        this.traceAnalysisMapper = traceAnalysisMapper;
    }

    @Override
    public TraceTurn findTurn(String turnId) {
        return traceAnalysisMapper.findTurn(turnId);
    }

    @Override
    public List<TraceTool> findTools(String turnId) {
        return traceAnalysisMapper.findTools(turnId);
    }

    @Override
    public List<HookEventEvidence> findHookEvents(String turnId) {
        return traceAnalysisMapper.findHookEvents(turnId);
    }

    @Override
    public List<PerformanceSpanEvidence> findPerformanceSpans(String turnId) {
        return traceAnalysisMapper.findPerformanceSpans(turnId);
    }

    @Override
    public List<PerformanceAlignmentEvidence> findAlignments(String turnId) {
        return traceAnalysisMapper.findAlignments(turnId);
    }
}
