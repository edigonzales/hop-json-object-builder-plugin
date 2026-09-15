package ch.so.agi.hop.json.builder.transform;

import ch.so.agi.hop.json.builder.core.JsonPointerEditor;
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
    id = "JSON_ARRAY_BUILDER",
    name = "JSON Array Builder",
    description =
        "Aggregates rows into a JSON array, optionally grouped by key fields and inserted into an"
            + " existing JSON document at a JSON Pointer (RFC 6901)",
    image = "ch/so/agi/hop/json/builder/transform/icons/json-array-builder.svg",
    categoryDescription = "JSON",
    keywords = {"json", "array builder", "aggregate", "json pointer", "rfc 6901", "stac", "links"})
public class JsonArrayBuilderMeta
    extends BaseTransformMeta<JsonArrayBuilder, JsonArrayBuilderData> {

  @HopMetadataProperty(key = "output_field")
  private String outputField;

  @HopMetadataProperty(key = "output_type")
  private JsonOutputType outputType;

  @HopMetadataProperty(key = "pretty_print")
  private boolean prettyPrint;

  @HopMetadataProperty(key = "element_mode")
  private JsonElementMode elementMode;

  @HopMetadataProperty(key = "element_field")
  private String elementField;

  @HopMetadataProperty(key = "element_type")
  private JsonValueType elementType;

  @HopMetadataProperty(key = "insert_into_target")
  private boolean insertIntoTarget;

  @HopMetadataProperty(key = "base_json_field")
  private String baseJsonField;

  @HopMetadataProperty(key = "json_pointer")
  private String jsonPointer;

  @HopMetadataProperty(key = "group_by_field", groupKey = "group_by_fields")
  private List<String> groupByFields;

  @HopMetadataProperty(key = "skip_null_elements")
  private boolean skipNullElements;

  public JsonArrayBuilderMeta() {
    super();
    groupByFields = new ArrayList<>();
    setDefault();
  }

  public JsonArrayBuilderMeta(JsonArrayBuilderMeta meta) {
    this();
    copyConfigurationFrom(meta);
  }

  void copyConfigurationFrom(JsonArrayBuilderMeta meta) {
    this.outputField = meta.outputField;
    this.outputType = meta.outputType;
    this.prettyPrint = meta.prettyPrint;
    this.elementMode = meta.elementMode;
    this.elementField = meta.elementField;
    this.elementType = meta.elementType;
    this.insertIntoTarget = meta.insertIntoTarget;
    this.baseJsonField = meta.baseJsonField;
    this.jsonPointer = meta.jsonPointer;
    this.groupByFields = new ArrayList<>(meta.getGroupByFields());
    this.skipNullElements = meta.skipNullElements;
  }

  @Override
  public Object clone() {
    return new JsonArrayBuilderMeta(this);
  }

  @Override
  public void setDefault() {
    outputField = "array";
    outputType = JsonOutputType.JSON;
    prettyPrint = false;
    elementMode = JsonElementMode.FIELD_VALUE;
    elementField = "";
    elementType = JsonValueType.AUTO;
    insertIntoTarget = false;
    baseJsonField = "";
    jsonPointer = "";
    groupByFields = new ArrayList<>();
    skipNullElements = false;
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

    IRowMeta inputRowMeta = rowMeta.clone();
    rowMeta.clear();
    for (String groupField : resolvedGroupByFields(variables)) {
      int index = inputRowMeta.indexOfValue(groupField);
      if (index < 0) {
        throw new HopTransformException("Group by field not found on the input row: " + groupField);
      }
      rowMeta.addValueMeta(inputRowMeta.getValueMeta(index));
    }
    rowMeta.addValueMeta(outputValueMeta);
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

    if (getElementMode() == JsonElementMode.FIELD_VALUE) {
      String field = JsonTransformSupport.resolve(variables, elementField);
      if (Utils.isEmpty(field)) {
        throw new HopTransformException("Element field is missing.");
      }
      if (inputRowMeta != null && inputRowMeta.indexOfValue(field) < 0) {
        throw new HopTransformException(
            "Element field '" + field + "' was not found on the input row.");
      }
    }

    if (isInsertIntoTarget()) {
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
      if (Utils.isEmpty(pointer)) {
        throw new HopTransformException(
            "JSON Pointer is missing for the insert into existing JSON object mode.");
      }
    }

    if (isGrouping() && inputRowMeta != null) {
      resolveGroupIndexes(inputRowMeta, variables);
    }
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
        throw new HopTransformException(
            "Group by field '" + field + "' was not found on the input row.");
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
              ICheckResult.TYPE_RESULT_OK,
              "JSON array builder configuration looks valid.",
              transformMeta));
    } catch (HopTransformException e) {
      remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), transformMeta));
    }

    if (isGrouping()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_COMMENT,
              "One JSON array is created per group of the group by fields; the input must be sorted"
                  + " by these fields.",
              transformMeta));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_COMMENT,
              "Without group by fields all input rows are collected into a single JSON array.",
              transformMeta));
    }
  }

  @Override
  public String getDialogClassName() {
    return JsonArrayBuilderDialog.class.getName();
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

  public JsonElementMode getElementMode() {
    return elementMode == null ? JsonElementMode.FIELD_VALUE : elementMode;
  }

  public void setElementMode(JsonElementMode elementMode) {
    this.elementMode = elementMode;
  }

  public String getElementField() {
    return elementField;
  }

  public void setElementField(String elementField) {
    this.elementField = elementField;
  }

  public JsonValueType getElementType() {
    return elementType == null ? JsonValueType.AUTO : elementType;
  }

  public void setElementType(JsonValueType elementType) {
    this.elementType = elementType;
  }

  public boolean isInsertIntoTarget() {
    return insertIntoTarget;
  }

  public void setInsertIntoTarget(boolean insertIntoTarget) {
    this.insertIntoTarget = insertIntoTarget;
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

  public List<String> getGroupByFields() {
    if (groupByFields == null) {
      groupByFields = new ArrayList<>();
    }
    return groupByFields;
  }

  public void setGroupByFields(List<String> groupByFields) {
    this.groupByFields = groupByFields;
  }

  public boolean isSkipNullElements() {
    return skipNullElements;
  }

  public void setSkipNullElements(boolean skipNullElements) {
    this.skipNullElements = skipNullElements;
  }
}
