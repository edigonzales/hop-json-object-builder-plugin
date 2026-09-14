package ch.so.agi.hop.json.builder.transform;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;
import org.apache.hop.pipeline.transform.ITransformData;

public class JsonArrayBuilderData extends BaseTransformData implements ITransformData {

  public IRowMeta inputRowMeta;
  public IRowMeta outputRowMeta;

  public boolean grouping;
  public int[] groupIndexes;

  public int elementIndex = -1;
  public String resolvedElementField;

  public boolean insertIntoTarget;
  public String resolvedBaseField;
  public int baseJsonIndex = -1;
  public JsonPointer jsonPointer = JsonPointer.compile("");

  public ArrayNode currentArray;
  public JsonNode currentBase;
  public Object[] currentGroupRow;
  public boolean hasCurrentGroup;

  public JsonArrayBuilderData() {
    super();
  }
}
