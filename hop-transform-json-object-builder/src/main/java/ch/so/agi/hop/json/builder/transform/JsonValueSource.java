package ch.so.agi.hop.json.builder.transform;

/** Where the JSON value of a mapping row comes from. */
public enum JsonValueSource {
  LITERAL("Literal value"),
  FIELD("Input field");

  private final String description;

  JsonValueSource(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public static String[] descriptions() {
    JsonValueSource[] values = values();
    String[] descriptions = new String[values.length];
    for (int i = 0; i < values.length; i++) {
      descriptions[i] = values[i].description;
    }
    return descriptions;
  }

  public static JsonValueSource lookupDescription(String description, JsonValueSource fallback) {
    for (JsonValueSource value : values()) {
      if (value.description.equalsIgnoreCase(description) || value.name().equalsIgnoreCase(description)) {
        return value;
      }
    }
    return fallback;
  }
}
