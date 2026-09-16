package dev.tracelens.interfaces.hookingestion;

/** Stable HTTP response for a newly accepted or duplicate Hook delivery. */
public record HookAcceptanceResponse(String deliveryId, String status) { }
