package ch.so.agi.hop.json.builder.transform;

/** Hop value type used for the JSON builder output field. */
public enum JsonOutputType {
  JSON("JSON (native)"),
  STRING("String");

  private final String description;

  JsonOutputType(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public static String[] descriptions() {
    JsonOutputType[] values = values();
    String[] descriptions = new String[values.length];
    for (int i = 0; i < values.length; i++) {
      descriptions[i] = values[i].description;
    }
    return descriptions;
  }

  public static JsonOutputType lookupDescription(String description, JsonOutputType fallback) {
    for (JsonOutputType value : values()) {
      if (value.description.equalsIgnoreCase(description) || value.name().equalsIgnoreCase(description)) {
        return value;
      }
    }
    return fallback;
  }
}
