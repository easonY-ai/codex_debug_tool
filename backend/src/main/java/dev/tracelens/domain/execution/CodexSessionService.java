package dev.tracelens.domain.execution;

/**
 * 推进单个 Session 聚合的领域服务。每次调用只加载目标 Session，并将当前状态与变更事实原子记录。
 */
public class CodexSessionService {
    private final CodexSessionRepository codexSessionRepository;

    public CodexSessionService(CodexSessionRepository codexSessionRepository) {
        this.codexSessionRepository = codexSessionRepository;
    }

    /** 应用一条已校验 Session 事实；Repository 写冲突向上抛出，由 Application 重放事务。 */
    public void applySessionFact(NormalizedHookFact fact) {
        CodexSession session = codexSessionRepository.findSession(fact.sessionId());
        codexSessionRepository.recordSessionState(session.applySessionFact(fact));
    }
}
