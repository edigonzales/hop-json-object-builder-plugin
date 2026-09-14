package ch.so.agi.hop.json.builder.transform;

import ch.so.agi.hop.json.builder.core.JsonValueType;

/** A {@link JsonMapping} with resolved variable text and input row indexes. */
final class ResolvedJsonMapping {

  final JsonKeySource keySource;
  final String keyText;
  final String keyFieldName;
  final int keyIndex;

  final JsonValueSource valueSource;
  final String valueText;
  final String valueFieldName;
  final int valueIndex;

  final JsonValueType valueType;
  final boolean skipIfNull;

  ResolvedJsonMapping(
      JsonKeySource keySource,
      String keyText,
      String keyFieldName,
      int keyIndex,
      JsonValueSource valueSource,
      String valueText,
      String valueFieldName,
      int valueIndex,
      JsonValueType valueType,
      boolean skipIfNull) {
    this.keySource = keySource;
    this.keyText = keyText;
    this.keyFieldName = keyFieldName;
    this.keyIndex = keyIndex;
    this.valueSource = valueSource;
    this.valueText = valueText;
    this.valueFieldName = valueFieldName;
    this.valueIndex = valueIndex;
    this.valueType = valueType;
    this.skipIfNull = skipIfNull;
  }

  boolean keyFromField() {
    return keySource == JsonKeySource.FIELD;
  }

  boolean valueFromField() {
    return valueSource == JsonValueSource.FIELD;
  }
}
