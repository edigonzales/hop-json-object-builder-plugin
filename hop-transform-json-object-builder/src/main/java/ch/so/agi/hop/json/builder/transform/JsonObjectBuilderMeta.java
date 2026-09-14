package ch.so.agi.hop.json.builder.transform;

import ch.so.agi.hop.json.builder.core.JsonPointerEditor;
import ch.so.agi.hop.json.builder.core.JsonValueConversion;
import ch.so.agi.hop.json.builder.core.JsonValueType;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaJson;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

@Transform(
    id = "JSON_OBJECT_BUILDER",
    name = "JSON Object Builder",
    description =
        "Builds JSON objects from typed field mappings, optionally inserted into an existing JSON document at a JSON Pointer (RFC 6901)",
    image = "ch/so/agi/hop/json/builder/transform/icons/json-object-builder.svg",
    categoryDescription = "JSON",
    keywords = {
      "json",
      "object builder",
      "json pointer",
      "rfc 6901",
      "stac",
      "geojson",
      "nested",
      "rest"
    })
public class JsonObjectBuilderMeta
    extends BaseTransformMeta<JsonObjectBuilder, JsonObjectBuilderData> {

  @HopMetadataProperty(key = "output_field")
  private String outputField;

  @HopMetadataProperty(key = "output_type")
  private JsonOutputType outputType;

  @HopMetadataProperty(key = "pretty_print")
  private boolean prettyPrint;

  @HopMetadataProperty(key = "mode")
  private JsonBuilderMode mode;

  @HopMetadataProperty(key = "base_json_field")
  private String baseJsonField;

  @HopMetadataProperty(key = "json_pointer")
  private String jsonPointer;

  @HopMetadataProperty(key = "mapping", groupKey = "mappings")
  private List<JsonMapping> mappings;

  @HopMetadataProperty(key = "group_by_field", groupKey = "group_by_fields")
  private List<String> groupByFields;

  public JsonObjectBuilderMeta() {
    super();
    mappings = new ArrayList<>();
    groupByFields = new ArrayList<>();
    setDefault();
  }

  public JsonObjectBuilderMeta(JsonObjectBuilderMeta meta) {
    this();
    this.outputField = meta.outputField;
    this.outputType = meta.outputType;
    this.prettyPrint = meta.prettyPrint;
    this.mode = meta.mode;
    this.baseJsonField = meta.baseJsonField;
    this.jsonPointer = meta.jsonPointer;
    this.mappings = new ArrayList<>();
    meta.getMappings().forEach(mapping -> this.mappings.add(new JsonMapping(mapping)));
    this.groupByFields = new ArrayList<>(meta.getGroupByFields());
  }

  @Override
  public Object clone() {
    return new JsonObjectBuilderMeta(this);
  }

  @Override
  public void setDefault() {
    outputField = "json";
    outputType = JsonOutputType.JSON;
    prettyPrint = false;
    mode = JsonBuilderMode.CREATE;
    baseJsonField = "";
    jsonPointer = "";
    mappings = new ArrayList<>();
    groupByFields = new ArrayList<>();
  }

  @Override
  public void getFields(
      IRowMeta rowMeta,
      String origin,
      IRowMeta[] info,
      TransformMeta nextTransform,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopTransformException {
    if (rowMeta == null) {
      return;
    }
    String name = JsonTransformSupport.resolve(variables, outputField);
    if (Utils.isEmpty(name)) {
      return;
    }

    IValueMeta outputValueMeta = createOutputValueMeta(name);
    outputValueMeta.setOrigin(origin);

    if (isGrouping()) {
      IRowMeta inputRowMeta = rowMeta.clone();
      rowMeta.clear();
      for (String groupField : getGroupByFields()) {
        if (Utils.isEmpty(groupField)) {
          continue;
        }
        int index = inputRowMeta.indexOfValue(groupField);
        if (index < 0) {
          throw new HopTransformException("Group by field not found on the input row: " + groupField);
        }
        rowMeta.addValueMeta(inputRowMeta.getValueMeta(index));
      }
      rowMeta.addValueMeta(outputValueMeta);
      return;
    }

    int existing = rowMeta.indexOfValue(name);
    if (existing >= 0) {
      rowMeta.setValueMeta(existing, outputValueMeta);
    } else {
      rowMeta.addValueMeta(outputValueMeta);
    }
  }

  /**
   * Validates the configuration against the input row metadata. Throws on the first problem; used
   * by the runtime and reported through {@link #check}.
   */
  public void validate(IRowMeta inputRowMeta, IVariables variables) throws HopTransformException {
    String resolvedOutput = JsonTransformSupport.resolve(variables, outputField);
    if (Utils.isEmpty(resolvedOutput)) {
      throw new HopTransformException("Output field is missing.");
    }

    JsonBuilderMode effectiveMode = getMode();
    if (effectiveMode == JsonBuilderMode.INSERT) {
      String baseField = JsonTransformSupport.resolve(variables, baseJsonField);
      if (Utils.isEmpty(baseField)) {
        throw new HopTransformException(
            "Base JSON field is missing for the insert into existing JSON object mode.");
      }
      if (inputRowMeta != null && inputRowMeta.indexOfValue(baseField) < 0) {
        throw new HopTransformException(
            "Base JSON field '" + baseField + "' was not found on the input row.");
      }
      String pointer = JsonTransformSupport.resolve(variables, jsonPointer);
      try {
        JsonPointerEditor.compile(pointer);
      } catch (IllegalArgumentException e) {
        throw new HopTransformException(e.getMessage(), e);
      }
    }

    if (getMappings().isEmpty()) {
      throw new HopTransformException("At least one field mapping is required.");
    }
    resolveMappings(inputRowMeta, variables);

    if (isGrouping() && inputRowMeta != null) {
      resolveGroupIndexes(inputRowMeta, variables);
    }
  }

  /** Resolves variables and input row indexes of all mapping rows. */
  public List<ResolvedJsonMapping> resolveMappings(IRowMeta inputRowMeta, IVariables variables)
      throws HopTransformException {
    List<ResolvedJsonMapping> resolved = new ArrayList<>();
    int row = 0;
    for (JsonMapping mapping : getMappings()) {
      row++;
      JsonKeySource keySource = mapping.getKeySource();
      String keyText = null;
      String keyField = null;
      int keyIndex = -1;
      if (keySource == JsonKeySource.LITERAL) {
        keyText = JsonTransformSupport.resolve(variables, mapping.getKey());
        if (keyText.isEmpty()) {
          throw new HopTransformException("Mapping row " + row + ": key is missing.");
        }
      } else {
        keyField = JsonTransformSupport.resolve(variables, mapping.getKeyField());
        if (keyField.isEmpty()) {
          throw new HopTransformException("Mapping row " + row + ": key field name is missing.");
        }
        if (inputRowMeta != null) {
          keyIndex = inputRowMeta.indexOfValue(keyField);
          if (keyIndex < 0) {
            throw new HopTransformException(
                "Mapping row " + row + ": key field '" + keyField + "' was not found on the input row.");
          }
        }
      }

      JsonValueSource valueSource = mapping.getValueSource();
      String valueText = null;
      String valueField = null;
      int valueIndex = -1;
      JsonValueType valueType = mapping.getValueType();
      if (valueSource == JsonValueSource.LITERAL) {
        valueText = JsonTransformSupport.resolve(variables, mapping.getValue());
        try {
          JsonValueConversion.literalToJson(valueText, valueType);
        } catch (IllegalArgumentException e) {
          throw new HopTransformException("Mapping row " + row + ": " + e.getMessage(), e);
        }
      } else {
        valueField = JsonTransformSupport.resolve(variables, mapping.getValueField());
        if (valueField.isEmpty()) {
          throw new HopTransformException("Mapping row " + row + ": value field name is missing.");
        }
        if (inputRowMeta != null) {
          valueIndex = inputRowMeta.indexOfValue(valueField);
          if (valueIndex < 0) {
            throw new HopTransformException(
                "Mapping row "
                    + row
                    + ": value field '"
                    + valueField
                    + "' was not found on the input row.");
          }
        }
      }

      resolved.add(
          new ResolvedJsonMapping(
              keySource,
              keyText,
              keyField,
              keyIndex,
              valueSource,
              valueText,
              valueField,
              valueIndex,
              valueType,
              mapping.isSkipIfNull()));
    }
    return resolved;
  }

  /** Resolves the input row indexes of the configured group by fields. */
  public int[] resolveGroupIndexes(IRowMeta inputRowMeta, IVariables variables)
      throws HopTransformException {
    List<String> fields = getGroupByFields();
    int[] indexes = new int[fields.size()];
    for (int i = 0; i < fields.size(); i++) {
      String field = JsonTransformSupport.resolve(variables, fields.get(i));
      if (Utils.isEmpty(field)) {
        throw new HopTransformException("Group by field " + (i + 1) + " is missing.");
      }
      int index = inputRowMeta.indexOfValue(field);
      if (index < 0) {
        throw new HopTransformException("Group by field '" + field + "' was not found on the input row.");
      }
      indexes[i] = index;
    }
    return indexes;
  }

  /** Resolved group by field names, used for the grouped output row. */
  public List<String> resolvedGroupByFields(IVariables variables) {
    List<String> resolved = new ArrayList<>();
    for (String field : getGroupByFields()) {
      if (!Utils.isEmpty(field)) {
        resolved.add(JsonTransformSupport.resolve(variables, field));
      }
    }
    return resolved;
  }

  public boolean isGrouping() {
    return getGroupByFields().stream().anyMatch(field -> !Utils.isEmpty(field));
  }

  public IValueMeta createOutputValueMeta(String name) {
    if (getOutputType() == JsonOutputType.STRING) {
      return new ValueMetaString(name);
    }
    ValueMetaJson valueMeta = new ValueMetaJson(name);
    valueMeta.setPrettyPrinting(isPrettyPrint());
    return valueMeta;
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      PipelineMeta pipelineMeta,
      TransformMeta transformMeta,
      IRowMeta prev,
      String[] input,
      String[] output,
      IRowMeta info,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (input == null || input.length == 0) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              "No input received from upstream transforms.",
              transformMeta));
      return;
    }
    if (prev == null) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              "No input row metadata is available from upstream transforms.",
              transformMeta));
      return;
    }

    try {
      validate(prev, variables);
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK, "JSON object builder configuration looks valid.", transformMeta));
    } catch (HopTransformException e) {
      remarks.add(
          new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), transformMeta));
    }

    if (isGrouping()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_COMMENT,
              "Grouped rows are merged into one JSON object per group; the input must be sorted by the group by fields.",
              transformMeta));
    }
    if (getMode() == JsonBuilderMode.CREATE && !Utils.isEmpty(jsonPointer)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              "The JSON Pointer is ignored when creating a new JSON object.",
              transformMeta));
    }
  }

  @Override
  public String getDialogClassName() {
    return JsonObjectBuilderDialog.class.getName();
  }

  public String getOutputField() {
    return outputField;
  }

  public void setOutputField(String outputField) {
    this.outputField = outputField;
  }

  public JsonOutputType getOutputType() {
    return outputType == null ? JsonOutputType.JSON : outputType;
  }

  public void setOutputType(JsonOutputType outputType) {
    this.outputType = outputType;
  }

  public boolean isPrettyPrint() {
    return prettyPrint;
  }

  public void setPrettyPrint(boolean prettyPrint) {
    this.prettyPrint = prettyPrint;
  }

  public JsonBuilderMode getMode() {
    return mode == null ? JsonBuilderMode.CREATE : mode;
  }

  public void setMode(JsonBuilderMode mode) {
    this.mode = mode;
  }

  public String getBaseJsonField() {
    return baseJsonField;
  }

  public void setBaseJsonField(String baseJsonField) {
    this.baseJsonField = baseJsonField;
  }

  public String getJsonPointer() {
    return jsonPointer;
  }

  public void setJsonPointer(String jsonPointer) {
    this.jsonPointer = jsonPointer;
  }

  public List<JsonMapping> getMappings() {
    if (mappings == null) {
      mappings = new ArrayList<>();
    }
    return mappings;
  }

  public void setMappings(List<JsonMapping> mappings) {
    this.mappings = mappings;
  }

  public List<String> getGroupByFields() {
    if (groupByFields == null) {
      groupByFields = new ArrayList<>();
    }
    return groupByFields;
  }

  public void setGroupByFields(List<String> groupByFields) {
    this.groupByFields = groupByFields;
  }
}
