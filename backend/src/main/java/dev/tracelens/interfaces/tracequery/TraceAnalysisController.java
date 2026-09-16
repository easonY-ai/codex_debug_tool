package dev.tracelens.interfaces.tracequery;

import dev.tracelens.application.tracequery.GetTraceAnalysisUseCase;
import dev.tracelens.application.tracequery.TraceAnalysisResult;
import dev.tracelens.domain.operationaldiagnostics.AuditedBusinessOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for one structured Trace detail response. */
@RestController
@RequestMapping("/api/sessions")
@AuditedBusinessOperations
public class TraceAnalysisController {
    private final GetTraceAnalysisUseCase useCase;

    public TraceAnalysisController(GetTraceAnalysisUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/{turnId}/analysis")
    public ResponseEntity<TraceAnalysisResult> analysis(@PathVariable String turnId) {
        TraceAnalysisResult result = useCase.get(turnId);
        return result == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(result);
    }
}
