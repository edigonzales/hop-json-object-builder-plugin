package ch.so.agi.hop.json.builder.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

/** Serializes JSON nodes for row output. */
public final class JsonNodeSerializer {

  private JsonNodeSerializer() {}

  public static String compact(JsonNode node) throws JsonProcessingException {
    return JsonSupport.MAPPER.writeValueAsString(node);
  }

  public static String pretty(JsonNode node) throws JsonProcessingException {
    return JsonSupport.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
  }
}
