package dev.tracelens.infrastructure.operationaldiagnostics;

import dev.tracelens.domain.execution.CodexTurnService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessOperationAuditAspectTest {
    @Test
    void executionDomainServicesAreAuditedWithoutAFrameworkAnnotationInDomain() throws Exception {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression("execution(public * *(..)) && ("
                + "@within(dev.tracelens.domain.operationaldiagnostics.AuditedBusinessOperations) "
                + "|| execution(public * dev.tracelens.domain.execution..*Service.*(..)))");

        assertThat(pointcut.matches(
                CodexTurnService.class.getMethod(
                        "applyTurnFact",
                        dev.tracelens.domain.execution.NormalizedHookFact.class),
                CodexTurnService.class)).isTrue();
    }

    @Test
    void summariesExposeOnlyTypeSizeAndStableHash() {
        String summary = BusinessOperationAuditAspect.summarizeArguments(new Object[]{
                "session-secret", new byte[]{1, 2, 3}, Map.of("prompt", "body-secret")
        });

        assertThat(summary).contains("String(length=14,sha256=");
        assertThat(summary).contains("bytes(length=3,sha256=");
        assertThat(summary).contains("Map(size=1)");
        assertThat(summary).doesNotContain("session-secret").doesNotContain("body-secret");
    }
}
