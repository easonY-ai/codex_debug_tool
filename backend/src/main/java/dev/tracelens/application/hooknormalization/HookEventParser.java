package dev.tracelens.application.hooknormalization;

import dev.tracelens.domain.hooknormalization.NormalizedHookEvent;

/** Application port that translates preserved raw Hook evidence into a domain value object. */
public interface HookEventParser {
    NormalizedHookEvent parse(String rawJson, long observedAt);
}
