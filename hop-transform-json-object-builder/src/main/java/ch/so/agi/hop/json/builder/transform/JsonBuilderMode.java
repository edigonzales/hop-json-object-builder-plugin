package ch.so.agi.hop.json.builder.transform;

/** How the JSON Object Builder produces its document. */
public enum JsonBuilderMode {
  CREATE("Create new JSON object"),
  INSERT("Insert into existing JSON object");

  private final String description;

  JsonBuilderMode(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public static String[] descriptions() {
    JsonBuilderMode[] values = values();
    String[] descriptions = new String[values.length];
    for (int i = 0; i < values.length; i++) {
      descriptions[i] = values[i].description;
    }
    return descriptions;
  }

  public static JsonBuilderMode lookupDescription(String description, JsonBuilderMode fallback) {
    for (JsonBuilderMode value : values()) {
      if (value.description.equalsIgnoreCase(description) || value.name().equalsIgnoreCase(description)) {
        return value;
      }
    }
    return fallback;
  }
}
