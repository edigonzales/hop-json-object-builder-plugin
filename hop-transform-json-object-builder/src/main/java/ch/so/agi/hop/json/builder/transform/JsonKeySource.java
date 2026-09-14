package ch.so.agi.hop.json.builder.transform;

/** Where the JSON object key of a mapping row comes from. */
public enum JsonKeySource {
  LITERAL("Literal"),
  FIELD("Field name");

  private final String description;

  JsonKeySource(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public static String[] descriptions() {
    JsonKeySource[] values = values();
    String[] descriptions = new String[values.length];
    for (int i = 0; i < values.length; i++) {
      descriptions[i] = values[i].description;
    }
    return descriptions;
  }

  public static JsonKeySource lookupDescription(String description, JsonKeySource fallback) {
    for (JsonKeySource value : values()) {
      if (value.description.equalsIgnoreCase(description) || value.name().equalsIgnoreCase(description)) {
        return value;
      }
    }
    return fallback;
  }
}
