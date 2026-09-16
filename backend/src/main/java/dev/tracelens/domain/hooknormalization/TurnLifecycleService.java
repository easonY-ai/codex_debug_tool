package dev.tracelens.domain.hooknormalization;

import dev.tracelens.domain.operationaldiagnostics.AuditedBusinessOperations;

/** Domain service that evolves and persists one Turn lifecycle from a normalized Hook event. */
@AuditedBusinessOperations
public class TurnLifecycleService {
    private final TurnRepository turnRepository;

    public TurnLifecycleService(TurnRepository turnRepository) {
        this.turnRepository = turnRepository;
    }

    public void evolveFrom(NormalizedHookEvent event) {
        if (event.turnId() == null) {
            return;
        }
        TurnLifecycle currentTurn = turnRepository.findOrEmpty(event.sessionId(), event.turnId());
        TurnLifecycle updatedTurn = currentTurn.evolveFrom(event);
        turnRepository.save(updatedTurn);
    }
}
