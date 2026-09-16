package dev.tracelens.infrastructure.hooknormalization;

import dev.tracelens.domain.hooknormalization.SessionLifecycleService;
import dev.tracelens.domain.hooknormalization.SessionRepository;
import dev.tracelens.domain.hooknormalization.ToolLifecycleService;
import dev.tracelens.domain.hooknormalization.ToolRepository;
import dev.tracelens.domain.hooknormalization.TurnLifecycleService;
import dev.tracelens.domain.hooknormalization.TurnRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires pure Hook-normalization domain services without adding Spring dependencies to Domain. */
@Configuration
public class HookNormalizationDomainConfiguration {
    @Bean
    SessionLifecycleService sessionLifecycleService(SessionRepository sessionRepository) {
        return new SessionLifecycleService(sessionRepository);
    }

    @Bean
    TurnLifecycleService turnLifecycleService(TurnRepository turnRepository) {
        return new TurnLifecycleService(turnRepository);
    }

    @Bean
    ToolLifecycleService toolLifecycleService(ToolRepository toolRepository) {
        return new ToolLifecycleService(toolRepository);
    }
}
