package ch.so.agi.hop.json.builder.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.so.agi.hop.json.builder.core.JsonValueType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.BlockingRowSet;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.IRowSet;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaBoolean;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonObjectBuilderRuntimeTest {

  @BeforeEach
  void initHop() throws Exception {
    HopEnvironment.init();
  }

  @AfterEach
  void resetHop() {
    HopEnvironment.reset();
  }

  @Test
  void createBuildsTypedJsonObjects() throws Exception {
    JsonObjectBuilderMeta meta = new JsonObjectBuilderMeta();
    meta.setOutputField("json");
    meta.getMappings().add(fieldMapping("name", "name", JsonValueType.AUTO));
    meta.getMappings().add(fieldMapping("count", "count", JsonValueType.AUTO));
    meta.getMappings().add(fieldMapping("active", "active", JsonValueType.AUTO));
    meta.getMappings().add(fieldMapping("payload", "payload", JsonValueType.JSON));
    JsonMapping roles = new JsonMapping();
    roles.setKey("roles");
    roles.setValueSource(JsonValueSource.LITERAL);
    roles.setValue("[\"data\"]");
    roles.setValueType(JsonValueType.JSON);
    meta.getMappings().add(roles);

    TestJsonObjectBuilder transform = newTransform(meta);

    RowMeta input = new RowMeta();
    input.addValueMeta(new ValueMetaString("name"));
    input.addValueMeta(new ValueMetaInteger("count"));
    input.addValueMeta(new ValueMetaNumber("price"));
    input.addValueMeta(new ValueMetaBoolean("active"));
    input.addValueMeta(new ValueMetaString("payload"));
    transform.setInput(
        input, List.<Object[]>of(new Object[] {"asset", 3L, 2.5d, true, "{\"a\":1}"}));

    List<Object[]> rows = runAndDrain(transform);

    assertThat(rows).hasSize(1);
    JsonNode node = (JsonNode) rows.get(0)[5];
    assertThat(node.get("name").asText()).isEqualTo("asset");
    assertThat(node.get("count").isIntegralNumber()).isTrue();
    assertThat(node.get("count").asLong()).isEqualTo(3L);
    assertThat(node.get("active").asBoolean()).isTrue();
    assertThat(node.get("payload").isObject()).isTrue();
    assertThat(node.get("payload").get("a").asInt()).isEqualTo(1);
    assertThat(node.get("roles").isArray()).isTrue();
    assertThat(node.get("roles").get(0).asText()).isEqualTo("data");
  }

  @Test
  void insertCreatesMissingObjectLevels() throws Exception {
    JsonObjectBuilderMeta meta = new JsonObjectBuilderMeta();
    meta.setOutputField("item_json");
    meta.setMode(JsonBuilderMode.INSERT);
    meta.setBaseJsonField("item_json");
    meta.setJsonPointer("/properties");
    meta.getMappings().add(fieldMapping("datetime", "datetime", JsonValueType.AUTO));
    meta.getMappings().add(fieldMapping("title", "title", JsonValueType.AUTO));

    TestJsonObjectBuilder transform = newTransform(meta);

    RowMeta input = new RowMeta();
    input.addValueMeta(new ValueMetaString("item_json"));
    input.addValueMeta(new ValueMetaString("datetime"));
    input.addValueMeta(new ValueMetaString("title"));
    transform.setInput(
        input,
        List.<Object[]>of(
            new Object[] {"{\"id\":\"item-1\"}", "2026-09-01T00:00:00Z", "Gemeindegrenzen"}));

    List<Object[]> rows = runAndDrain(transform);

    assertThat(rows).hasSize(1);
    JsonNode node = (JsonNode) rows.get(0)[0];
    assertThat(node.get("id").asText()).isEqualTo("item-1");
    assertThat(node.get("properties").get("datetime").asText()).isEqualTo("2026-09-01T00:00:00Z");
    assertThat(node.get("properties").get("title").asText()).isEqualTo("Gemeindegrenzen");
  }

  @Test
  void groupedInsertMergesDynamicKeysIntoOneObjectPerGroup() throws Exception {
    JsonObjectBuilderMeta meta = new JsonObjectBuilderMeta();
    meta.setOutputField("item_json");
    meta.setMode(JsonBuilderMode.INSERT);
    meta.setBaseJsonField("item_json");
    meta.setJsonPointer("/assets");
    meta.getGroupByFields().add("item_id");

    JsonMapping mapping = new JsonMapping();
    mapping.setKeySource(JsonKeySource.FIELD);
    mapping.setKeyField("asset_key");
    mapping.setValueSource(JsonValueSource.FIELD);
    mapping.setValueField("asset_json");
    mapping.setValueType(JsonValueType.JSON);
    meta.getMappings().add(mapping);

    TestJsonObjectBuilder transform = newTransform(meta);

    RowMeta input = new RowMeta();
    input.addValueMeta(new ValueMetaString("item_id"));
    input.addValueMeta(new ValueMetaString("asset_key"));
    input.addValueMeta(new ValueMetaString("asset_json"));
    input.addValueMeta(new ValueMetaString("item_json"));
    transform.setInput(
        input,
        List.of(
            new Object[] {"item-1", "geoparquet", "{\"href\":\"a.parquet\"}", "{\"id\":\"item-1\"}"},
            new Object[] {"item-1", "interlis", "{\"href\":\"a.xtf\"}", "{\"id\":\"item-1\"}"}));

    List<Object[]> rows = runAndDrain(transform);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)[0]).isEqualTo("item-1");
    JsonNode node = (JsonNode) rows.get(0)[1];
    assertThat(node.get("id").asText()).isEqualTo("item-1");
    assertThat(node.get("assets").get("geoparquet").get("href").asText()).isEqualTo("a.parquet");
    assertThat(node.get("assets").get("interlis").get("href").asText()).isEqualTo("a.xtf");
  }

  @Test
  void skipIfNullControlsWhetherKeysAreWritten() throws Exception {
    JsonObjectBuilderMeta meta = new JsonObjectBuilderMeta();
    meta.setOutputField("json");
    JsonMapping skipped = fieldMapping("a", "value", JsonValueType.AUTO);
    skipped.setSkipIfNull(true);
    JsonMapping written = fieldMapping("b", "value", JsonValueType.AUTO);
    meta.getMappings().add(skipped);
    meta.getMappings().add(written);

    TestJsonObjectBuilder transform = newTransform(meta);

    RowMeta input = new RowMeta();
    input.addValueMeta(new ValueMetaString("value"));
    transform.setInput(input, List.<Object[]>of(new Object[] {null}));

    List<Object[]> rows = runAndDrain(transform);

    JsonNode node = (JsonNode) rows.get(0)[1];
    assertThat(node.has("a")).isFalse();
    assertThat(node.has("b")).isTrue();
    assertThat(node.get("b").isNull()).isTrue();
  }

  @Test
  void skipIfNullAlsoSkipsEmptyStrings() throws Exception {
    JsonObjectBuilderMeta meta = new JsonObjectBuilderMeta();
    meta.setOutputField("json");
    JsonMapping skipped = fieldMapping("type", "media_type", JsonValueType.AUTO);
    skipped.setSkipIfNull(true);
    JsonMapping written = fieldMapping("media_type", "media_type", JsonValueType.AUTO);
    meta.getMappings().add(skipped);
    meta.getMappings().add(written);

    TestJsonObjectBuilder transform = newTransform(meta);

    RowMeta input = new RowMeta();
    input.addValueMeta(new ValueMetaString("media_type"));
    transform.setInput(input, List.<Object[]>of(new Object[] {""}));

    List<Object[]> rows = runAndDrain(transform);

    JsonNode node = (JsonNode) rows.get(0)[1];
    assertThat(node.has("type")).isFalse();
    assertThat(node.get("media_type").asText()).isEmpty();
  }

  @Test
  void rowWiseInsertReplacesTheBaseFieldInPlace() throws Exception {
    JsonObjectBuilderMeta meta = new JsonObjectBuilderMeta();
    meta.setOutputField("item_json");
    meta.setMode(JsonBuilderMode.INSERT);
    meta.setBaseJsonField("item_json");
    meta.setJsonPointer("");
    meta.getMappings().add(fieldMapping("id", "id", JsonValueType.AUTO));

    TestJsonObjectBuilder transform = newTransform(meta);

    RowMeta input = new RowMeta();
    input.addValueMeta(new ValueMetaString("id"));
    input.addValueMeta(new ValueMetaString("item_json"));
    transform.setInput(input, List.<Object[]>of(new Object[] {"item-1", "{\"existing\":true}"}));

    List<Object[]> rows = runAndDrain(transform);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)).hasSize(2);
    JsonNode node = (JsonNode) rows.get(0)[1];
    assertThat(node.get("id").asText()).isEqualTo("item-1");
    assertThat(node.get("existing").asBoolean()).isTrue();
  }

  @Test
  void insertRejectsInvalidBaseJson() {
    JsonObjectBuilderMeta meta = new JsonObjectBuilderMeta();
    meta.setOutputField("item_json");
    meta.setMode(JsonBuilderMode.INSERT);
    meta.setBaseJsonField("item_json");
    meta.getMappings().add(fieldMapping("id", "id", JsonValueType.AUTO));

    TestJsonObjectBuilder transform = newTransform(meta);

    RowMeta input = new RowMeta();
    input.addValueMeta(new ValueMetaString("id"));
    input.addValueMeta(new ValueMetaString("item_json"));
    transform.setInput(input, List.<Object[]>of(new Object[] {"item-1", "{invalid"}));
    addOutputRowSet(transform);

    assertThatThrownBy(() -> run(transform))
        .isInstanceOf(HopTransformException.class)
        .hasMessageContaining("does not contain valid JSON");
  }

  @Test
  void insertRejectsNullBaseJson() {
    JsonObjectBuilderMeta meta = new JsonObjectBuilderMeta();
    meta.setOutputField("item_json");
    meta.setMode(JsonBuilderMode.INSERT);
    meta.setBaseJsonField("item_json");
    meta.getMappings().add(fieldMapping("id", "id", JsonValueType.AUTO));

    TestJsonObjectBuilder transform = newTransform(meta);

    RowMeta input = new RowMeta();
    input.addValueMeta(new ValueMetaString("id"));
    input.addValueMeta(new ValueMetaString("item_json"));
    transform.setInput(input, List.<Object[]>of(new Object[] {"item-1", null}));
    addOutputRowSet(transform);

    assertThatThrownBy(() -> run(transform))
        .isInstanceOf(HopTransformException.class)
        .hasMessageContaining("is null");
  }

  @Test
  void stringOutputIsSerializedCompactlyOrPretty() throws Exception {
    JsonObjectBuilderMeta meta = new JsonObjectBuilderMeta();
    meta.setOutputField("json");
    meta.setOutputType(JsonOutputType.STRING);
    meta.getMappings().add(fieldMapping("id", "id", JsonValueType.AUTO));

    TestJsonObjectBuilder transform = newTransform(meta);

    RowMeta input = new RowMeta();
    input.addValueMeta(new ValueMetaString("id"));
    transform.setInput(input, List.<Object[]>of(new Object[] {"item-1"}));

    List<Object[]> rows = runAndDrain(transform);

    assertThat(rows.get(0)[1]).isEqualTo("{\"id\":\"item-1\"}");

    meta.setPrettyPrint(true);
    TestJsonObjectBuilder prettyTransform = newTransform(meta);
    prettyTransform.setInput(input, List.<Object[]>of(new Object[] {"item-1"}));

    List<Object[]> prettyRows = runAndDrain(prettyTransform);

    assertThat((String) prettyRows.get(0)[1]).contains("\n");
  }

  private JsonMapping fieldMapping(String key, String field, JsonValueType type) {
    JsonMapping mapping = new JsonMapping();
    mapping.setKey(key);
    mapping.setValueSource(JsonValueSource.FIELD);
    mapping.setValueField(field);
    mapping.setValueType(type);
    return mapping;
  }

  private TestJsonObjectBuilder newTransform(JsonObjectBuilderMeta meta) {
    return new TestJsonObjectBuilder(
        new TransformMeta("builder", meta), meta, new JsonObjectBuilderData(), new PipelineMeta());
  }

  private BlockingRowSet addOutputRowSet(TestJsonObjectBuilder transform) {
    BlockingRowSet output = new BlockingRowSet(10);
    output.setThreadNameFromToCopy("builder", 0, "main", 0);
    transform.addRowSetToOutputRowSets(output);
    return output;
  }

  private List<Object[]> runAndDrain(TestJsonObjectBuilder transform) throws HopException {
    BlockingRowSet output = addOutputRowSet(transform);
    run(transform);
    List<Object[]> rows = new ArrayList<>();
    Object[] row;
    while ((row = output.getRow()) != null) {
      rows.add(row);
    }
    return rows;
  }

  private void run(JsonObjectBuilder transform) throws HopException {
    while (transform.processRow()) {
      // keep consuming until the transform signals completion
    }
  }

  private static class TestJsonObjectBuilder extends JsonObjectBuilder {

    private IRowMeta inputRowMeta;
    private List<Object[]> inputRows = List.of();
    private int inputIndex;

    TestJsonObjectBuilder(
        TransformMeta transformMeta,
        JsonObjectBuilderMeta meta,
        JsonObjectBuilderData data,
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
