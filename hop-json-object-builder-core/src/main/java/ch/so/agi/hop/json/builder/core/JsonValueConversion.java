package ch.so.agi.hop.json.builder.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

/** Converts literal configuration text into JSON nodes. */
public final class JsonValueConversion {

  private JsonValueConversion() {}

  /**
   * Converts a configured literal value to a JSON node.
   *
   * <p>{@link JsonValueType#AUTO} and {@link JsonValueType#STRING} produce a JSON string. {@link
   * JsonValueType#JSON} parses the text as JSON, so literals such as {@code ["data"]} or {@code
   * {"href": "..."}} become real objects and arrays.
   *
   * @throws IllegalArgumentException when the text does not match the configured type
   */
  public static JsonNode literalToJson(String text, JsonValueType type) {
    JsonValueType effective = type == null ? JsonValueType.AUTO : type;
    if (effective == JsonValueType.NULL) {
      return JsonSupport.NODES.nullNode();
    }

    String value = text == null ? "" : text;
    try {
      return switch (effective) {
        case AUTO, STRING -> JsonSupport.NODES.textNode(value);
        case INTEGER -> JsonSupport.NODES.numberNode(Long.parseLong(value.trim()));
        case NUMBER -> JsonSupport.NODES.numberNode(Double.parseDouble(value.trim()));
        case BIGNUMBER -> JsonSupport.NODES.numberNode(new BigDecimal(value.trim()));
        case BOOLEAN -> JsonSupport.NODES.booleanNode(parseBoolean(value.trim()));
        case JSON -> JsonSupport.MAPPER.readTree(value);
        case NULL -> JsonSupport.NODES.nullNode();
      };
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Value '" + value + "' cannot be converted to " + effective.getDescription(), e);
    }
  }

  private static boolean parseBoolean(String text) {
    if ("true".equalsIgnoreCase(text)) {
      return true;
    }
    if ("false".equalsIgnoreCase(text)) {
      return false;
    }
    throw new IllegalArgumentException("Not a boolean: " + text);
  }
}
