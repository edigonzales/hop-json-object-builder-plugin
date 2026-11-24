package ch.so.agi.hop.json.builder.transform;

import ch.so.agi.hop.commons.core.SourceMode;
import ch.so.agi.hop.json.builder.core.JsonValueType;

/** A {@link JsonMapping} with resolved variable text and input row indexes. */
final class ResolvedJsonMapping {

  final SourceMode keySource;
  final String keyText;
  final String keyFieldName;
  final int keyIndex;

  final SourceMode valueSource;
  final String valueText;
  final String valueFieldName;
  final int valueIndex;

  final JsonValueType valueType;
  final boolean skipIfNull;

  ResolvedJsonMapping(
      SourceMode keySource,
      String keyText,
      String keyFieldName,
      int keyIndex,
      SourceMode valueSource,
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
    return keySource == SourceMode.FIELD;
  }

  boolean valueFromField() {
    return valueSource == SourceMode.FIELD;
  }
}
