package dev.tracelens.domain.hooknormalization;

import dev.tracelens.domain.operationaldiagnostics.AuditedBusinessOperations;

/** Domain service that evolves and persists one Tool lifecycle from a normalized Hook event. */
@AuditedBusinessOperations
public class ToolLifecycleService {
    private final ToolRepository toolRepository;

    public ToolLifecycleService(ToolRepository toolRepository) {
        this.toolRepository = toolRepository;
    }

    public void evolveFrom(NormalizedHookEvent event) {
        if (!event.isToolEvent()) {
            return;
        }
        if (event.turnId() == null || event.toolUseId() == null) {
            throw new IllegalArgumentException("tool event lacks aggregate key");
        }
        ToolLifecycle currentTool = toolRepository.findOrEmpty(event.sessionId(), event.turnId(), event.toolUseId());
        ToolLifecycle updatedTool = currentTool.evolveFrom(event);
        toolRepository.save(updatedTool);
    }
}
