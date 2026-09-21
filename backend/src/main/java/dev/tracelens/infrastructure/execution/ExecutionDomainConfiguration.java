package dev.tracelens.infrastructure.execution;

import dev.tracelens.domain.execution.CodexSessionRepository;
import dev.tracelens.domain.execution.CodexSessionService;
import dev.tracelens.domain.execution.CodexTurnRepository;
import dev.tracelens.domain.execution.CodexTurnService;
import dev.tracelens.domain.execution.ToolCallRepository;
import dev.tracelens.domain.execution.ToolCallService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 组装纯 Execution Domain 服务，Domain 不依赖 Spring 或审计基础设施。 */
@Configuration
public class ExecutionDomainConfiguration {
    @Bean
    CodexSessionService codexSessionService(CodexSessionRepository codexSessionRepository) {
        return new CodexSessionService(codexSessionRepository);
    }

    @Bean
    CodexTurnService codexTurnService(CodexTurnRepository codexTurnRepository) {
        return new CodexTurnService(codexTurnRepository);
    }

    @Bean
    ToolCallService toolCallService(ToolCallRepository toolCallRepository) {
        return new ToolCallService(toolCallRepository);
    }
}
