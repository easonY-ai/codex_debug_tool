package dev.tracelens.interfaces.hookingestion;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/** Dedicated raw-evidence REST type; it is intentionally not a generic JSON business DTO. */
@JsonDeserialize(using = RawHookEventRequestDeserializer.class)
public record RawHookEventRequest(String rawJson) { }
