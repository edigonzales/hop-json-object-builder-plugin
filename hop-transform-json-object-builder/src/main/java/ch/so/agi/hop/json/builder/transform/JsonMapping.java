package ch.so.agi.hop.json.builder.transform;

import ch.so.agi.hop.commons.core.SourceMode;
import ch.so.agi.hop.json.builder.core.JsonValueType;
import java.util.Objects;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * One key/value mapping of the JSON Object Builder. The key and the value can each be configured as
 * a literal or read from an input field.
 */
public class JsonMapping implements Cloneable {

  @HopMetadataProperty(key = "key_source")
  private SourceMode keySource = SourceMode.CONFIGURED;

  @HopMetadataProperty(key = "key")
  private String key;

  @HopMetadataProperty(key = "key_field")
  private String keyField;

  @HopMetadataProperty(key = "value_source")
  private SourceMode valueSource = SourceMode.FIELD;

  @HopMetadataProperty(key = "value")
  private String value;

  @HopMetadataProperty(key = "value_field")
  private String valueField;

  @HopMetadataProperty(key = "value_type")
  private JsonValueType valueType = JsonValueType.AUTO;

  @HopMetadataProperty(key = "skip_if_null")
  private boolean skipIfNull;

  public JsonMapping() {}

  public JsonMapping(JsonMapping mapping) {
    this.keySource = mapping.getKeySource();
    this.key = mapping.key;
    this.keyField = mapping.keyField;
    this.valueSource = mapping.getValueSource();
    this.value = mapping.value;
    this.valueField = mapping.valueField;
    this.valueType = mapping.getValueType();
    this.skipIfNull = mapping.skipIfNull;
  }

  @Override
  public JsonMapping clone() {
    return new JsonMapping(this);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof JsonMapping that)) {
      return false;
    }
    return skipIfNull == that.skipIfNull
        && getKeySource() == that.getKeySource()
        && Objects.equals(key, that.key)
        && Objects.equals(keyField, that.keyField)
        && getValueSource() == that.getValueSource()
        && Objects.equals(value, that.value)
        && Objects.equals(valueField, that.valueField)
        && getValueType() == that.getValueType();
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        getKeySource(), key, keyField, getValueSource(), value, valueField, getValueType(), skipIfNull);
  }

  public SourceMode getKeySource() {
    return keySource == null ? SourceMode.CONFIGURED : keySource;
  }

  public void setKeySource(SourceMode keySource) {
    this.keySource = keySource;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getKeyField() {
    return keyField;
  }

  public void setKeyField(String keyField) {
    this.keyField = keyField;
  }

  public SourceMode getValueSource() {
    return valueSource == null ? SourceMode.FIELD : valueSource;
  }

  public void setValueSource(SourceMode valueSource) {
    this.valueSource = valueSource;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public String getValueField() {
    return valueField;
  }

  public void setValueField(String valueField) {
    this.valueField = valueField;
  }

  public JsonValueType getValueType() {
    return valueType == null ? JsonValueType.AUTO : valueType;
  }

  public void setValueType(JsonValueType valueType) {
    this.valueType = valueType;
  }

  public boolean isSkipIfNull() {
    return skipIfNull;
  }

  public void setSkipIfNull(boolean skipIfNull) {
    this.skipIfNull = skipIfNull;
  }
}
