package ch.so.agi.hop.json.builder.core;

/**
 * Value types offered by the JSON builder transforms. {@link #AUTO} derives the JSON type from the
 * input field type; the explicit types force a conversion. The enum names are also the tokens used
 * in pipeline XML, so they must remain stable.
 */
public enum JsonValueType {
  AUTO("Automatic (use the field type)"),
  STRING("String"),
  INTEGER("Integer"),
  NUMBER("Number"),
  BIGNUMBER("Big number"),
  BOOLEAN("Boolean"),
  JSON("JSON"),
  NULL("Null");

  private final String description;

  JsonValueType(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public static String[] descriptions() {
    JsonValueType[] values = values();
    String[] descriptions = new String[values.length];
    for (int i = 0; i < values.length; i++) {
      descriptions[i] = values[i].description;
    }
    return descriptions;
  }

  public static JsonValueType lookupDescription(String description, JsonValueType fallback) {
    for (JsonValueType value : values()) {
      if (value.description.equalsIgnoreCase(description) || value.name().equalsIgnoreCase(description)) {
        return value;
      }
    }
    return fallback;
  }
}
