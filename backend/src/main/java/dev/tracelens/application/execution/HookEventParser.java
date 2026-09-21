package dev.tracelens.application.execution;

import dev.tracelens.domain.execution.NormalizedHookFact;

/** 将版本化 Hook 原始证据转换为 Execution Published Language 的出站端口。 */
public interface HookEventParser {
    NormalizedHookFact parse(String rawJson, long observedAt);
}
