package ch.so.agi.hop.json.builder.transform;

import ch.so.agi.hop.json.builder.core.JsonPointerEditor;
import ch.so.agi.hop.json.builder.core.JsonSupport;
import ch.so.agi.hop.json.builder.core.JsonValueConversion;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * Builds JSON objects from typed field mappings. With configured group by fields all rows of a
 * group are merged into a single JSON object; without grouping the transform works row by row.
 */
public class JsonObjectBuilder extends BaseTransform<JsonObjectBuilderMeta, JsonObjectBuilderData> {

  public JsonObjectBuilder(
      TransformMeta transformMeta,
      JsonObjectBuilderMeta meta,
      JsonObjectBuilderData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    Object[] row = getRow();
    if (row == null) {
      if (!first && data.grouping) {
        emitCurrentGroup();
      }
      setOutputDone();
      return false;
    }

    if (first) {
      first = false;
      initialize();
    }

    if (data.grouping) {
      boolean newGroup =
          !data.hasCurrentGroup
              || data.inputRowMeta.compare(data.currentGroupRow, row, data.groupIndexes) != 0;
      if (newGroup) {
        emitCurrentGroup();
        startDocument(row);
      }
      applyMappings(row);
      data.currentGroupRow = data.inputRowMeta.cloneRow(row);
      data.hasCurrentGroup = true;
      return true;
    }

    startDocument(row);
    applyMappings(row);

    Object output =
        JsonTransformSupport.outputValue(
            data.currentRoot, meta.getOutputType(), meta.isPrettyPrint());
    Object[] outputRow;
    if (data.replaceInPlace) {
      outputRow = data.inputRowMeta.cloneRow(row);
      outputRow[data.outputFieldIndex] = output;
    } else {
      outputRow = RowDataUtil.addValueData(row, data.inputSize, output);
    }
    putRow(data.outputRowMeta, outputRow);
    incrementLinesOutput();
    return true;
  }

  private void initialize() throws HopException {
    data.inputRowMeta = getInputRowMeta();
    data.inputSize = data.inputRowMeta.size();
    data.outputRowMeta = data.inputRowMeta.clone();
    meta.getFields(data.outputRowMeta, getTransformName(), null, null, this, metadataProvider);
    meta.validate(data.inputRowMeta, this);

    String outputName = JsonTransformSupport.resolve(this, meta.getOutputField());
    data.outputFieldIndex = data.outputRowMeta.indexOfValue(outputName);
    if (data.outputFieldIndex < 0) {
      throw new HopTransformException(
          "Output field was not found on the output row: " + outputName);
    }

    data.grouping = meta.isGrouping();
    data.replaceInPlace = !data.grouping && data.inputRowMeta.indexOfValue(outputName) >= 0;
    data.mappings = meta.resolveMappings(data.inputRowMeta, this);

    if (data.grouping) {
      data.groupIndexes = meta.resolveGroupIndexes(data.inputRowMeta, this);
      logBasic(
          "Merging rows into one JSON object per group of "
              + meta.resolvedGroupByFields(this)
              + "; the input must be sorted by these fields.");
    }

    if (meta.getMode() == JsonBuilderMode.INSERT) {
      data.resolvedBaseField = JsonTransformSupport.resolve(this, meta.getBaseJsonField());
      data.baseJsonIndex = data.inputRowMeta.indexOfValue(data.resolvedBaseField);
      data.jsonPointer =
          JsonPointerEditor.compile(JsonTransformSupport.resolve(this, meta.getJsonPointer()));
    } else {
      data.jsonPointer = JsonPointer.compile("");
    }
  }

  private void startDocument(Object[] row) throws HopTransformException {
    if (meta.getMode() == JsonBuilderMode.INSERT) {
      JsonNode base =
          JsonTransformSupport.readJsonField(
              data.inputRowMeta, row, data.baseJsonIndex, data.resolvedBaseField);
      if (base == null || base.isNull()) {
        throw new HopTransformException(
            "Base JSON field '"
                + data.resolvedBaseField
                + "' is null; the insert into existing JSON object mode needs an existing JSON"
                + " document.");
      }
      data.currentRoot = base;
    } else {
      data.currentRoot = JsonSupport.NODES.objectNode();
    }

    try {
      data.currentTarget = JsonPointerEditor.resolveObject(data.currentRoot, data.jsonPointer);
    } catch (IllegalArgumentException e) {
      throw new HopTransformException(
          "Unable to resolve JSON Pointer '" + data.jsonPointer + "': " + e.getMessage(), e);
    }
  }

  private void applyMappings(Object[] row) throws HopTransformException {
    for (ResolvedJsonMapping mapping : data.mappings) {
      String key = keyForMapping(mapping, row);
      if (key == null) {
        continue;
      }
      if (key.isEmpty()) {
        if (mapping.skipIfNull) {
          continue;
        }
        throw new HopTransformException("A mapping produced an empty JSON key.");
      }

      JsonNode value = valueForMapping(mapping, row, key);
      if (JsonTransformSupport.isMissing(value)) {
        if (mapping.skipIfNull) {
          continue;
        }
        if (value == null) {
          value = JsonSupport.NODES.nullNode();
        }
      }
      data.currentTarget.set(key, value);
    }
  }

  private String keyForMapping(ResolvedJsonMapping mapping, Object[] row)
      throws HopTransformException {
    if (!mapping.keyFromField()) {
      return mapping.keyText;
    }
    String key;
    try {
      key = data.inputRowMeta.getString(row, mapping.keyIndex);
    } catch (Exception e) {
      throw new HopTransformException(
          "Unable to read key field '" + mapping.keyFieldName + "': " + e.getMessage(), e);
    }
    if (key == null && !mapping.skipIfNull) {
      throw new HopTransformException(
          "Key field '" + mapping.keyFieldName + "' returned no value for a mapping row.");
    }
    return key;
  }

  private JsonNode valueForMapping(ResolvedJsonMapping mapping, Object[] row, String key)
      throws HopTransformException {
    if (mapping.valueFromField()) {
      return JsonTransformSupport.fieldValue(
          data.inputRowMeta,
          row,
          mapping.valueIndex,
          mapping.valueType,
          mapping.valueFieldName,
          mapping.skipIfNull);
    }
    if (mapping.skipIfNull && mapping.valueText.isEmpty()) {
      return null;
    }
    try {
      return JsonValueConversion.literalToJson(mapping.valueText, mapping.valueType);
    } catch (IllegalArgumentException e) {
      throw new HopTransformException("Mapping '" + key + "': " + e.getMessage(), e);
    }
  }

  private void emitCurrentGroup() throws HopException {
    if (!data.hasCurrentGroup || data.currentRoot == null) {
      return;
    }

    Object output =
        JsonTransformSupport.outputValue(
            data.currentRoot, meta.getOutputType(), meta.isPrettyPrint());
    Object[] outputRow = new Object[data.groupIndexes.length + 1];
    for (int i = 0; i < data.groupIndexes.length; i++) {
      outputRow[i] = data.currentGroupRow[data.groupIndexes[i]];
    }
    outputRow[data.groupIndexes.length] = output;
    putRow(data.outputRowMeta, outputRow);
    incrementLinesOutput();

    data.currentRoot = null;
    data.currentTarget = null;
    data.currentGroupRow = null;
    data.hasCurrentGroup = false;
  }
}
