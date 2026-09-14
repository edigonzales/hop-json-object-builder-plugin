package ch.so.agi.hop.json.builder.transform;

/** Element source of the JSON Array Builder. */
public enum JsonElementMode {
  FIELD_VALUE("Field value"),
  WHOLE_ROW("Whole input row as JSON object");

  private final String description;

  JsonElementMode(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public static String[] descriptions() {
    JsonElementMode[] values = values();
    String[] descriptions = new String[values.length];
    for (int i = 0; i < values.length; i++) {
      descriptions[i] = values[i].description;
    }
    return descriptions;
  }

  public static JsonElementMode lookupDescription(String description, JsonElementMode fallback) {
    for (JsonElementMode value : values()) {
      if (value.description.equalsIgnoreCase(description) || value.name().equalsIgnoreCase(description)) {
        return value;
      }
    }
    return fallback;
  }
}
