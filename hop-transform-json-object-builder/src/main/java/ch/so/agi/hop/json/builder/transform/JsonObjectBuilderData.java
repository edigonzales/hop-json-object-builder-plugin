package ch.so.agi.hop.json.builder.transform;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;
import org.apache.hop.pipeline.transform.ITransformData;

public class JsonObjectBuilderData extends BaseTransformData implements ITransformData {

  public IRowMeta inputRowMeta;
  public IRowMeta outputRowMeta;
  public int inputSize;
  public int outputFieldIndex;

  public boolean grouping;
  public int[] groupIndexes;
  public boolean replaceInPlace;

  public List<ResolvedJsonMapping> mappings = new ArrayList<>();

  public String resolvedBaseField;
  public int baseJsonIndex = -1;
  public JsonPointer jsonPointer = JsonPointer.compile("");

  public JsonNode currentRoot;
  public ObjectNode currentTarget;
  public Object[] currentGroupRow;
  public boolean hasCurrentGroup;

  public JsonObjectBuilderData() {
    super();
  }
}
