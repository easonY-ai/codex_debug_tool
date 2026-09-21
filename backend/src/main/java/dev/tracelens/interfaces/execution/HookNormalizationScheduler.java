package dev.tracelens.interfaces.execution;

import dev.tracelens.application.execution.NormalizeNextHookDeliveryUseCase;
import dev.tracelens.domain.operationaldiagnostics.AuditedBusinessOperations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时触发下一条 Hook delivery 标准化；不承担协议解析、事务或生命周期规则。 */
@Component
@AuditedBusinessOperations
@ConditionalOnProperty(name = "trace-lens.normalization.enabled", matchIfMissing = true)
public class HookNormalizationScheduler {
    private final NormalizeNextHookDeliveryUseCase normalizeNextHookDeliveryUseCase;

    public HookNormalizationScheduler(NormalizeNextHookDeliveryUseCase normalizeNextHookDeliveryUseCase) {
        this.normalizeNextHookDeliveryUseCase = normalizeNextHookDeliveryUseCase;
    }

    /** 触发一次有界轮询；无待处理任务时正常返回，业务失败由用例稳定分类。 */
    @Scheduled(fixedDelayString = "${trace-lens.normalization.poll-ms:100}")
    public void poll() {
        normalizeNextHookDeliveryUseCase.normalizeNextHookDelivery();
    }
}
