package ch.so.agi.hop.json.builder.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/** Shared Jackson primitives for the JSON builder core. */
public final class JsonSupport {

  public static final ObjectMapper MAPPER = new ObjectMapper();
  public static final JsonNodeFactory NODES = JsonNodeFactory.instance;

  private JsonSupport() {}

  /**
   * Parses JSON text. Returns {@code null} for {@code null} input; a JSON {@code null} literal
   * results in {@link JsonNode#isNull()}.
   */
  public static JsonNode parse(String text) throws JsonProcessingException {
    if (text == null) {
      return null;
    }
    return MAPPER.readTree(text);
  }
}
