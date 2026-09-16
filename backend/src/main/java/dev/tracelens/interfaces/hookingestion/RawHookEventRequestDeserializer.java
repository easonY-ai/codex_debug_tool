package dev.tracelens.interfaces.hookingestion;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/** Jackson-only boundary adapter that preserves a raw event object as evidence text. */
public final class RawHookEventRequestDeserializer extends JsonDeserializer<RawHookEventRequest> {
    @Override public RawHookEventRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (node == null || !node.isObject()) throw context.weirdStringException("rawEvent", RawHookEventRequest.class, "rawEvent must be an object");
        return new RawHookEventRequest(node.toString());
    }
}
