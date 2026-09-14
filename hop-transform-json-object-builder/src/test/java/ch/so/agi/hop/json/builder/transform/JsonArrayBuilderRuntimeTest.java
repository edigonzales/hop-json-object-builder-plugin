package ch.so.agi.hop.json.builder.transform;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.json.builder.core.JsonValueType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.BlockingRowSet;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.IRowSet;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonArrayBuilderRuntimeTest {

  @BeforeEach
  void initHop() throws Exception {
    HopEnvironment.init();
  }

  @AfterEach
  void resetHop() {
    HopEnvironment.reset();
  }

  @Test
  void collectsAllRowsIntoASingleArray() throws Exception {
    JsonArrayBuilderMeta meta = new JsonArrayBuilderMeta();
    meta.setOutputField("ids");
    meta.setElementField("id");
    meta.setElementType(JsonValueType.AUTO);

    TestJsonArrayBuilder transform = newTransform(meta);

    RowMeta input = new RowMeta();
    input.addValueMeta(new ValueMetaInteger("id"));
    transform.setInput(input, List.of(new Object[] {1L}, new Object[] {2L}, new Object[] {3L}));

    List<Object[]> rows = runAndDrain(transform);

    assertThat(rows).hasSize(1);
    JsonNode array = (JsonNode) rows.get(0)[0];
    assertThat(array.isArray()).isTrue();
    assertThat(array).hasSize(3);
    assertThat(array.get(0).asLong()).isEqualTo(1L);
    assertThat(array.get(2).asLong()).isEqualTo(3L);
  }

  @Test
  void emptyInputProducesAnEmptyArray() throws Exception {
    JsonArrayBuilderMeta meta = new JsonArrayBuilderMeta();
    meta.setOutputField("ids");
    meta.setElementField("id");

    TestJsonArrayBuilder transform = newTransform(meta);

    RowMeta input = new RowMeta();
    input.addValueMeta(new ValueMetaInteger("id"));
    transform.setInput(input, List.of());

    List<Object[]> rows = runAndDrain(transform);

    assertThat(rows).hasSize(1);
    JsonNode array = (JsonNode) rows.get(0)[0];
    assertThat(array.isArray()).isTrue();
    assertThat(array).isEmpty();
  }

  @Test
  void createsOneArrayPerGroup() throws Exception {
    JsonArrayBuilderMeta meta = new JsonArrayBuilderMeta();
    meta.setOutputField("links");
    meta.setElementField("href");
    meta.getGroupByFields().add("item_id");

    TestJsonArrayBuilder transform = newTransform(meta);

    RowMeta input = new RowMeta();
    input.addValueMeta(new ValueMetaString("item_id"));
    input.addValueMeta(new ValueMetaString("href"));
    transform.setInput(
        input,
        List.of(
            new Object[] {"item-1", "a"},
            new Object[] {"item-1", "b"},
            new Object[] {"item-2", "c"}));

    List<Object[]> rows = runAndDrain(transform);

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0)[0]).isEqualTo("item-1");
    JsonNode first = (JsonNode) rows.get(0)[1];
    assertThat(first).hasSize(2);
    assertThat(rows.get(1)[0]).isEqualTo("item-2");
    JsonNode second = (JsonNode) rows.get(1)[1];
    assertThat(second).hasSize(1);
    assertThat(second.get(0).asText()).isEqualTo("c");
  }

  @Test
  void wholeRowElementsBecomeJsonObjects() throws Exception {
    JsonArrayBuilderMeta meta = new JsonArrayBuilderMeta();
    meta.setOutputField("links");
    meta.setElementMode(JsonElementMode.WHOLE_ROW);

    TestJsonArrayBuilder transform = newTransform(meta);

    RowMeta input = new RowMeta();
    input.addValueMeta(new ValueMetaString("rel"));
    input.addValueMeta(new ValueMetaString("href"));
    transform.setInput(input, List.<Object[]>of(new Object[] {"self", "https://example.org/"}, new Object[] {"root", null}));

    List<Object[]> rows = runAndDrain(transform);

    JsonNode array = (JsonNode) rows.get(0)[0];
    assertThat(array).hasSize(2);
    assertThat(array.get(0).get("rel").asText()).isEqualTo("self");
    assertThat(array.get(0).get("href").asText()).isEqualTo("https://example.org/");
    assertThat(array.get(1).get("href").isNull()).isTrue();
  }

  @Test
  void wholeRowElementsCanSkipNullFields() throws Exception {
    JsonArrayBuilderMeta meta = new JsonArrayBuilderMeta();
    meta.setOutputField("links");
    meta.setElementMode(JsonElementMode.WHOLE_ROW);
    meta.setSkipNullElements(true);

    TestJsonArrayBuilder transform = newTransform(meta);

    RowMeta input = new RowMeta();
    input.addValueMeta(new ValueMetaString("rel"));
    input.addValueMeta(new ValueMetaString("href"));
    transform.setInput(input, List.<Object[]>of(new Object[] {"root", null}));

    List<Object[]> rows = runAndDrain(transform);

    JsonNode array = (JsonNode) rows.get(0)[0];
    assertThat(array.get(0).has("href")).isFalse();
  }

  @Test
  void skipNullElementsOmitsNullFieldValues() throws Exception {
    JsonArrayBuilderMeta meta = new JsonArrayBuilderMeta();
    meta.setOutputField("ids");
    meta.setElementField("id");
    meta.setSkipNullElements(true);

    TestJsonArrayBuilder transform = newTransform(meta);

    RowMeta input = new RowMeta();
    input.addValueMeta(new ValueMetaInteger("id"));
    transform.setInput(input, List.<Object[]>of(new Object[] {1L}, new Object[] {null}, new Object[] {2L}));

    List<Object[]> rows = runAndDrain(transform);

    JsonNode array = (JsonNode) rows.get(0)[0];
    assertThat(array).hasSize(2);
  }

  @Test
  void skipNullElementsAlsoSkipsEmptyStrings() throws Exception {
    JsonArrayBuilderMeta meta = new JsonArrayBuilderMeta();
    meta.setOutputField("values");
    meta.setElementField("value");
    meta.setSkipNullElements(true);

    TestJsonArrayBuilder transform = newTransform(meta);

    RowMeta input = new RowMeta();
    input.addValueMeta(new ValueMetaString("value"));
    transform.setInput(input, List.<Object[]>of(new Object[] {"a"}, new Object[] {""}, new Object[] {"b"}));

    List<Object[]> rows = runAndDrain(transform);

    JsonNode array = (JsonNode) rows.get(0)[0];
    assertThat(array).hasSize(2);
  }

  @Test
  void jsonElementsBecomeRealObjects() throws Exception {
    JsonArrayBuilderMeta meta = new JsonArrayBuilderMeta();
    meta.setOutputField("links");
    meta.setElementField("link_json");
    meta.setElementType(JsonValueType.JSON);

    TestJsonArrayBuilder transform = newTransform(meta);

    RowMeta input = new RowMeta();
    input.addValueMeta(new ValueMetaString("link_json"));
    transform.setInput(
        input,
        List.<Object[]>of(
            new Object[] {"{\"rel\":\"self\"}"}, new Object[] {"{\"rel\":\"root\"}"}));

    List<Object[]> rows = runAndDrain(transform);

    JsonNode array = (JsonNode) rows.get(0)[0];
    assertThat(array.get(0).isObject()).isTrue();
    assertThat(array.get(0).get("rel").asText()).isEqualTo("self");
  }

  @Test
  void insertsTheArrayIntoAnExistingJsonDocument() throws Exception {
    JsonArrayBuilderMeta meta = new JsonArrayBuilderMeta();
    meta.setOutputField("item_json");
    meta.setElementField("href");
    meta.setInsertIntoTarget(true);
    meta.setBaseJsonField("item_json");
    meta.setJsonPointer("/links");

    TestJsonArrayBuilder transform = newTransform(meta);

    RowMeta input = new RowMeta();
    input.addValueMeta(new ValueMetaString("href"));
    input.addValueMeta(new ValueMetaString("item_json"));
    transform.setInput(
        input,
        List.of(
            new Object[] {"self", "{\"id\":\"item-1\"}"},
            new Object[] {"root", "{\"id\":\"item-1\"}"}));

    List<Object[]> rows = runAndDrain(transform);

    assertThat(rows).hasSize(1);
    JsonNode node = (JsonNode) rows.get(0)[0];
    assertThat(node.get("id").asText()).isEqualTo("item-1");
    assertThat(node.get("links")).hasSize(2);
    assertThat(node.get("links").get(0).asText()).isEqualTo("self");
  }

  private TestJsonArrayBuilder newTransform(JsonArrayBuilderMeta meta) {
    return new TestJsonArrayBuilder(
        new TransformMeta("array-builder", meta), meta, new JsonArrayBuilderData(), new PipelineMeta());
  }

  private List<Object[]> runAndDrain(TestJsonArrayBuilder transform) throws HopException {
    BlockingRowSet output = new BlockingRowSet(10);
    output.setThreadNameFromToCopy("array-builder", 0, "main", 0);
    transform.addRowSetToOutputRowSets(output);
    while (transform.processRow()) {
      // keep consuming until the transform signals completion
    }
    List<Object[]> rows = new ArrayList<>();
    Object[] row;
    while ((row = output.getRow()) != null) {
      rows.add(row);
    }
    return rows;
  }

  private static class TestJsonArrayBuilder extends JsonArrayBuilder {

    private IRowMeta inputRowMeta;
    private List<Object[]> inputRows = List.of();
    private int inputIndex;

    TestJsonArrayBuilder(
        TransformMeta transformMeta,
        JsonArrayBuilderMeta meta,
        JsonArrayBuilderData data,
        PipelineMeta pipelineMeta) {
      super(transformMeta, meta, data, 0, pipelineMeta, null);
    }

    @Override
    public void dispatch() {
      // Tests attach output row sets explicitly.
    }

    void setInput(IRowMeta rowMeta, List<Object[]> rows) {
      this.inputRowMeta = rowMeta;
      this.inputRows = new ArrayList<>(rows);
      this.inputIndex = 0;
    }

    @Override
    public Object[] getRow() {
      if (inputIndex >= inputRows.size()) {
        return null;
      }
      return inputRows.get(inputIndex++);
    }

    @Override
    public IRowMeta getInputRowMeta() {
      return inputRowMeta;
    }

    @Override
    public void putRow(IRowMeta rowMeta, Object[] row) {
      for (IRowSet rowSet : getOutputRowSets()) {
        rowSet.putRow(rowMeta, row);
      }
    }
  }
}
