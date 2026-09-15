package ch.so.agi.hop.json.builder.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.so.agi.hop.json.builder.core.JsonSupport;
import ch.so.agi.hop.json.builder.core.JsonValueType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaJson;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class JsonBuilderRegressionTest {
  @BeforeEach
  void init() throws Exception {
    HopEnvironment.init();
  }

  @AfterEach
  void reset() {
    HopEnvironment.reset();
  }

  @ParameterizedTest
  @ValueSource(strings = {"base", "result"})
  void objectSelfMappingCopiesTheOriginalDocument(String output) throws Exception {
    JsonObjectBuilderMeta meta = objectMeta();
    meta.setMode(JsonBuilderMode.INSERT);
    meta.setBaseJsonField("base");
    meta.setOutputField(output);
    meta.getMappings().clear();
    meta.getMappings().add(fieldMapping("self", "base", JsonValueType.JSON, false));
    ObjectNode base = (ObjectNode) JsonSupport.parse("{\"nested\":{\"id\":1}}");
    RowMeta fields = new RowMeta();
    fields.addValueMeta(new ValueMetaJson("base"));
    ObjectRunner runner =
        new ObjectRunner(meta, fields, List.of(new Object[] {base}, new Object[] {base}));
    runner.processRow();
    JsonNode first = (JsonNode) runner.output.getFirst()[output.equals("base") ? 0 : 1];
    assertThat(JsonSupport.parse(first.toString())).isEqualTo(first);
    assertThat(base.has("self")).isFalse();
    String firstText = first.toString();
    ((ObjectNode) base.get("nested")).put("id", 2);
    runner.processRow();
    runner.processRow();
    assertThat(first.toString()).isEqualTo(firstText);
    assertThat(first.path("self").path("nested").path("id").asInt()).isEqualTo(1);
  }

  @Test
  void mappingsOwnTheirNestedJsonFragments() throws Exception {
    JsonObjectBuilderMeta meta = objectMeta();
    ObjectNode fragment = (ObjectNode) JsonSupport.parse("{\"nested\":{\"id\":1}}");
    RowMeta fields = new RowMeta();
    fields.addValueMeta(new ValueMetaJson("value"));
    ObjectRunner runner =
        new ObjectRunner(meta, fields, List.<Object[]>of(new Object[] {fragment}));
    runner.processRow();
    JsonNode result = (JsonNode) runner.output.getFirst()[1];
    ((ObjectNode) fragment.get("nested")).put("id", 2);
    assertThat(result.path("value").path("nested").path("id").asInt()).isEqualTo(1);
  }

  @ParameterizedTest
  @EnumSource(JsonElementMode.class)
  void arraysCopyNativeElementsAndInsertionBase(JsonElementMode mode) throws Exception {
    JsonArrayBuilderMeta meta = arrayMeta();
    meta.setElementMode(mode);
    meta.setInsertIntoTarget(true);
    meta.setBaseJsonField("value");
    meta.setJsonPointer("/items");
    meta.getGroupByFields().add("group");
    ObjectNode base = (ObjectNode) JsonSupport.parse("{\"nested\":{\"id\":1}}");
    RowMeta fields = new RowMeta();
    fields.addValueMeta(new ValueMetaJson("value"));
    fields.addValueMeta(new ValueMetaString("group"));
    ArrayRunner runner =
        new ArrayRunner(meta, fields, List.of(new Object[] {base, "a"}, new Object[] {base, "b"}));
    runner.processRow();
    runner.processRow();
    runner.processRow();
    JsonNode first = (JsonNode) runner.output.getFirst()[1];
    JsonNode second = (JsonNode) runner.output.get(1)[1];
    String firstText = first.toString();
    assertThat(JsonSupport.parse(firstText)).isEqualTo(first);
    assertThat(base.has("items")).isFalse();
    ((ObjectNode) base.get("nested")).put("id", 2);
    ((ObjectNode) second).put("later", true);
    assertThat(first.toString()).isEqualTo(firstText);
  }

  @ParameterizedTest
  @EnumSource(JsonValueType.class)
  void skipsEmptyFieldsBeforeForcedConversion(JsonValueType type) throws Exception {
    JsonObjectBuilderMeta meta = objectMeta();
    meta.getMappings().getFirst().setValueType(type);
    meta.getMappings().getFirst().setSkipIfNull(true);
    ObjectRunner object =
        new ObjectRunner(meta, stringFields(), List.<Object[]>of(new Object[] {""}));
    object.processRow();
    assertThat((JsonNode) object.output.getFirst()[1]).isEmpty();
    JsonArrayBuilderMeta am = arrayMeta();
    am.setElementType(type);
    am.setSkipNullElements(true);
    ArrayRunner array = new ArrayRunner(am, stringFields(), List.<Object[]>of(new Object[] {""}));
    array.processRow();
    array.processRow();
    assertThat((JsonNode) array.output.getFirst()[0]).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void jsonNullAndMissingNodesFollowSkipFlag(boolean skip) throws Exception {
    RowMeta fields = new RowMeta();
    fields.addValueMeta(new ValueMetaJson("value"));
    for (Object value :
        new Object[] {null, JsonSupport.NODES.nullNode(), JsonSupport.NODES.missingNode()}) {
      JsonObjectBuilderMeta om = objectMeta();
      om.getMappings().getFirst().setSkipIfNull(skip);
      ObjectRunner object = new ObjectRunner(om, fields, List.<Object[]>of(new Object[] {value}));
      object.processRow();
      JsonNode result = (JsonNode) object.output.getFirst()[1];
      assertThat(result.has("value")).isEqualTo(!skip);
      JsonArrayBuilderMeta am = arrayMeta();
      am.setSkipNullElements(skip);
      ArrayRunner array = new ArrayRunner(am, fields, List.<Object[]>of(new Object[] {value}));
      array.processRow();
      array.processRow();
      assertThat((JsonNode) array.output.getFirst()[0]).hasSize(skip ? 0 : 1);
      assertThat(JsonTransformSupport.rowObject(fields, new Object[] {value}, skip).has("value"))
          .isEqualTo(!skip);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void explicitNullFieldsAndLiteralsFollowSkipFlag(boolean skip) throws Exception {
    for (JsonValueType type : List.of(JsonValueType.NULL, JsonValueType.JSON)) {
      JsonObjectBuilderMeta om = objectMeta();
      JsonMapping mapping = om.getMappings().getFirst();
      mapping.setValueType(type);
      mapping.setValueSource(JsonValueSource.LITERAL);
      mapping.setValue("null");
      mapping.setSkipIfNull(skip);
      ObjectRunner object =
          new ObjectRunner(om, stringFields(), List.<Object[]>of(new Object[] {"null"}));
      object.processRow();
      assertThat(((JsonNode) object.output.getFirst()[1]).has("value")).isEqualTo(!skip);
      JsonArrayBuilderMeta am = arrayMeta();
      am.setElementType(type);
      am.setSkipNullElements(skip);
      ArrayRunner array =
          new ArrayRunner(am, stringFields(), List.<Object[]>of(new Object[] {"null"}));
      array.processRow();
      array.processRow();
      assertThat((JsonNode) array.output.getFirst()[0]).hasSize(skip ? 0 : 1);
    }
  }

  @Test
  void emptyLiteralsSkipValidationButInvalidNonemptyLiteralsStillFail() throws Exception {
    JsonObjectBuilderMeta meta = objectMeta();
    JsonMapping mapping = meta.getMappings().getFirst();
    mapping.setValueSource(JsonValueSource.LITERAL);
    mapping.setValueType(JsonValueType.INTEGER);
    mapping.setValue("");
    mapping.setSkipIfNull(true);
    ObjectRunner runner =
        new ObjectRunner(meta, stringFields(), List.<Object[]>of(new Object[] {"unused"}));
    runner.processRow();
    assertThat((JsonNode) runner.output.getFirst()[1]).isEmpty();
    mapping.setSkipIfNull(false);
    assertThatThrownBy(() -> meta.validate(stringFields(), null))
        .hasMessageContaining("Mapping row 1");
    mapping.setSkipIfNull(true);
    mapping.setValue("bad");
    assertThatThrownBy(() -> meta.validate(stringFields(), null))
        .hasMessageContaining("Mapping row 1");
  }

  @Test
  void invalidFieldsAndTrailingJsonFailWithFieldContext() {
    for (boolean skip : List.of(false, true)) {
      assertThatThrownBy(
              () ->
                  JsonTransformSupport.fieldValue(
                      stringFields(),
                      new Object[] {"bad"},
                      0,
                      JsonValueType.INTEGER,
                      "value",
                      skip))
          .hasMessageContaining("field 'value'");
      assertThatThrownBy(
              () ->
                  JsonTransformSupport.fieldValue(
                      stringFields(), new Object[] {"{} {}"}, 0, JsonValueType.JSON, "value", skip))
          .hasMessageContaining("field 'value'");
    }
    assertThatThrownBy(
            () ->
                JsonTransformSupport.readJsonField(
                    stringFields(), new Object[] {"{} {}"}, 0, "base"))
        .hasMessageContaining("Field 'base'");
  }

  @Test
  void preservesEmptyAndWhitespaceStringsWithoutBroadeningSkip() throws Exception {
    for (boolean skip : List.of(false, true)) {
      JsonArrayBuilderMeta meta = arrayMeta();
      meta.setSkipNullElements(skip);
      ArrayRunner runner =
          new ArrayRunner(meta, stringFields(), List.of(new Object[] {""}, new Object[] {" "}));
      runner.processRow();
      runner.processRow();
      runner.processRow();
      JsonNode result = (JsonNode) runner.output.getFirst()[0];
      assertThat(result).hasSize(skip ? 1 : 2);
      assertThat(result.get(result.size() - 1).asText()).isEqualTo(" ");
    }
  }

  @Test
  void resolvesGroupVariablesForMetadataAndRuntime() throws Exception {
    JsonObjectBuilderMeta meta = objectMeta();
    meta.getGroupByFields().add("${GROUP}");
    ObjectRunner runner =
        new ObjectRunner(meta, stringFields(), List.<Object[]>of(new Object[] {"a"}));
    runner.setVariable("GROUP", "value");
    runner.processRow();
    runner.processRow();
    assertThat(runner.output.getFirst()[0]).isEqualTo("a");
    assertThat(runner.resultMeta.getValueMeta(0).getName()).isEqualTo("value");
    ObjectRunner invalid =
        new ObjectRunner(meta, stringFields(), List.<Object[]>of(new Object[] {"a"}));
    invalid.setVariable("GROUP", "unknown");
    assertThatThrownBy(invalid::processRow)
        .isInstanceOf(HopTransformException.class)
        .hasMessageContaining("unknown");
  }

  static RowMeta stringFields() {
    RowMeta fields = new RowMeta();
    fields.addValueMeta(new ValueMetaString("value"));
    return fields;
  }

  static JsonMapping fieldMapping(String key, String field, JsonValueType type, boolean skip) {
    JsonMapping mapping = new JsonMapping();
    mapping.setKey(key);
    mapping.setValueField(field);
    mapping.setValueType(type);
    mapping.setSkipIfNull(skip);
    return mapping;
  }

  static JsonObjectBuilderMeta objectMeta() {
    JsonObjectBuilderMeta meta = new JsonObjectBuilderMeta();
    meta.getMappings().add(fieldMapping("value", "value", JsonValueType.AUTO, false));
    return meta;
  }

  static JsonArrayBuilderMeta arrayMeta() {
    JsonArrayBuilderMeta meta = new JsonArrayBuilderMeta();
    meta.setElementField("value");
    return meta;
  }

  static class ObjectRunner extends JsonObjectBuilder {
    final IRowMeta fields;
    final List<Object[]> rows;
    final List<Object[]> output = new ArrayList<>();
    IRowMeta resultMeta;
    int index;

    ObjectRunner(JsonObjectBuilderMeta meta, IRowMeta fields, List<Object[]> rows) {
      super(
          new TransformMeta("object", meta),
          meta,
          new JsonObjectBuilderData(),
          0,
          new PipelineMeta(),
          null);
      this.fields = fields;
      this.rows = rows;
    }

    @Override
    public void dispatch() {}

    @Override
    public Object[] getRow() {
      return index < rows.size() ? rows.get(index++) : null;
    }

    @Override
    public IRowMeta getInputRowMeta() {
      return fields;
    }

    @Override
    public void putRow(IRowMeta meta, Object[] row) {
      resultMeta = meta;
      output.add(row);
    }
  }

  static class ArrayRunner extends JsonArrayBuilder {
    final IRowMeta fields;
    final List<Object[]> rows;
    final List<Object[]> output = new ArrayList<>();
    int index;

    ArrayRunner(JsonArrayBuilderMeta meta, IRowMeta fields, List<Object[]> rows) {
      super(
          new TransformMeta("array", meta),
          meta,
          new JsonArrayBuilderData(),
          0,
          new PipelineMeta(),
          null);
      this.fields = fields;
      this.rows = rows;
    }

    @Override
    public void dispatch() {}

    @Override
    public Object[] getRow() {
      return index < rows.size() ? rows.get(index++) : null;
    }

    @Override
    public IRowMeta getInputRowMeta() {
      return fields;
    }

    @Override
    public void putRow(IRowMeta meta, Object[] row) {
      output.add(row);
    }
  }
}
