package dev.tracelens.domain.execution;

/** 推进单个 Turn 聚合的领域服务；始终使用 sessionId 与 turnId 完整身份。 */
public class CodexTurnService {
    private final CodexTurnRepository codexTurnRepository;

    public CodexTurnService(CodexTurnRepository codexTurnRepository) {
        this.codexTurnRepository = codexTurnRepository;
    }

    /** 应用一条 Turn 事实；无 turnId 的 Session 级事实不会创建 Turn。 */
    public void applyTurnFact(NormalizedHookFact fact) {
        if (fact.turnId() == null) {
            return;
        }
        CodexTurn turn = codexTurnRepository.findTurn(fact.sessionId(), fact.turnId());
        codexTurnRepository.recordTurnState(turn.applyTurnFact(fact));
    }
}
