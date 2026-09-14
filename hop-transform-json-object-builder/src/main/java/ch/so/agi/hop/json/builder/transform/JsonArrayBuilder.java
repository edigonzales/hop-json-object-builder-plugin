package ch.so.agi.hop.json.builder.transform;

import ch.so.agi.hop.json.builder.core.JsonPointerEditor;
import ch.so.agi.hop.json.builder.core.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * Aggregates input rows into a JSON array. With configured group by fields one array is created per
 * group; without grouping all rows become a single array. The array can be emitted as field or
 * inserted into an existing JSON document at a JSON Pointer.
 */
public class JsonArrayBuilder extends BaseTransform<JsonArrayBuilderMeta, JsonArrayBuilderData> {

  public JsonArrayBuilder(
      TransformMeta transformMeta,
      JsonArrayBuilderMeta meta,
      JsonArrayBuilderData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    Object[] row = getRow();

    if (first) {
      first = false;
      if (getInputRowMeta() == null) {
        setOutputDone();
        return false;
      }
      initialize();
    }

    if (row == null) {
      if (data.grouping) {
        emitCurrentGroup();
      } else {
        emitSingleArray();
      }
      setOutputDone();
      return false;
    }

    if (data.grouping) {
      boolean newGroup =
          !data.hasCurrentGroup
              || data.inputRowMeta.compare(data.currentGroupRow, row, data.groupIndexes) != 0;
      if (newGroup) {
        emitCurrentGroup();
        startGroup(row);
      }
      addElement(row);
      data.currentGroupRow = data.inputRowMeta.cloneRow(row);
      data.hasCurrentGroup = true;
      return true;
    }

    if (data.currentArray == null) {
      data.currentArray = JsonSupport.NODES.arrayNode();
      if (data.insertIntoTarget) {
        data.currentBase = readBaseRow(row);
      }
    }
    addElement(row);
    return true;
  }

  private void initialize() throws HopException {
    data.inputRowMeta = getInputRowMeta();
    data.outputRowMeta = data.inputRowMeta.clone();
    meta.getFields(data.outputRowMeta, getTransformName(), null, null, this, metadataProvider);
    meta.validate(data.inputRowMeta, this);

    data.grouping = meta.isGrouping();
    if (data.grouping) {
      data.groupIndexes = meta.resolveGroupIndexes(data.inputRowMeta, this);
      logBasic(
          "Creating one JSON array per group of "
              + meta.resolvedGroupByFields(this)
              + "; the input must be sorted by these fields.");
    }

    if (meta.getElementMode() == JsonElementMode.FIELD_VALUE) {
      data.resolvedElementField = JsonTransformSupport.resolve(this, meta.getElementField());
      data.elementIndex = data.inputRowMeta.indexOfValue(data.resolvedElementField);
      if (data.elementIndex < 0) {
        throw new HopTransformException(
            "Element field '" + data.resolvedElementField + "' was not found on the input row.");
      }
    }

    data.insertIntoTarget = meta.isInsertIntoTarget();
    if (data.insertIntoTarget) {
      data.resolvedBaseField = JsonTransformSupport.resolve(this, meta.getBaseJsonField());
      data.baseJsonIndex = data.inputRowMeta.indexOfValue(data.resolvedBaseField);
      data.jsonPointer =
          JsonPointerEditor.compile(JsonTransformSupport.resolve(this, meta.getJsonPointer()));
    }
  }

  private void startGroup(Object[] row) throws HopTransformException {
    data.currentArray = JsonSupport.NODES.arrayNode();
    data.currentBase = data.insertIntoTarget ? readBaseRow(row) : null;
  }

  private JsonNode readBaseRow(Object[] row) throws HopTransformException {
    JsonNode base =
        JsonTransformSupport.readJsonField(
            data.inputRowMeta, row, data.baseJsonIndex, data.resolvedBaseField);
    if (base == null || base.isNull()) {
      throw new HopTransformException(
          "Base JSON field '"
              + data.resolvedBaseField
              + "' is null; the insert into existing JSON object mode needs an existing JSON document.");
    }
    return base;
  }

  private void addElement(Object[] row) throws HopTransformException {
    JsonNode element;
    if (meta.getElementMode() == JsonElementMode.WHOLE_ROW) {
      element =
          JsonTransformSupport.rowObject(data.inputRowMeta, row, meta.isSkipNullElements());
    } else {
      element =
          JsonTransformSupport.fieldValue(
              data.inputRowMeta, row, data.elementIndex, meta.getElementType(), data.resolvedElementField);
    }

    if (JsonTransformSupport.isMissing(element)) {
      if (meta.isSkipNullElements()) {
        return;
      }
      if (element == null) {
        element = JsonSupport.NODES.nullNode();
      }
    }
    data.currentArray.add(element);
  }

  private void emitCurrentGroup() throws HopException {
    if (!data.hasCurrentGroup || data.currentArray == null) {
      return;
    }

    Object[] outputRow = new Object[data.groupIndexes.length + 1];
    for (int i = 0; i < data.groupIndexes.length; i++) {
      outputRow[i] = data.currentGroupRow[data.groupIndexes[i]];
    }
    outputRow[data.groupIndexes.length] = buildOutputValue();

    putRow(data.outputRowMeta, outputRow);
    incrementLinesOutput();

    data.currentArray = null;
    data.currentBase = null;
    data.currentGroupRow = null;
    data.hasCurrentGroup = false;
  }

  private void emitSingleArray() throws HopException {
    if (data.currentArray == null) {
      data.currentArray = JsonSupport.NODES.arrayNode();
    }
    Object[] outputRow = new Object[] {buildOutputValue()};
    putRow(data.outputRowMeta, outputRow);
    incrementLinesOutput();

    data.currentArray = null;
    data.currentBase = null;
  }

  private Object buildOutputValue() throws HopException {
    JsonNode outputNode = data.currentArray;
    if (data.insertIntoTarget) {
      if (data.currentBase == null) {
        throw new HopTransformException(
            "No base JSON document is available for the insert into existing JSON object mode.");
      }
      try {
        JsonPointerEditor.setArray(data.currentBase, data.jsonPointer, data.currentArray);
      } catch (IllegalArgumentException e) {
        throw new HopTransformException(
            "Unable to insert the JSON array at '"
                + data.jsonPointer
                + "': "
                + e.getMessage(),
            e);
      }
      outputNode = data.currentBase;
    }
    return JsonTransformSupport.outputValue(outputNode, meta.getOutputType(), meta.isPrettyPrint());
  }
}
