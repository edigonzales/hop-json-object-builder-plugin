package ch.so.agi.hop.json.builder.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.so.agi.hop.json.builder.core.JsonValueType;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaJson;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonObjectBuilderMetaTest {

  @BeforeEach
  void initHop() throws Exception {
    HopEnvironment.init();
  }

  @AfterEach
  void resetHop() {
    HopEnvironment.reset();
  }

  @Test
  void defaultsCreateJsonObjects() {
    JsonObjectBuilderMeta meta = new JsonObjectBuilderMeta();

    assertThat(meta.getOutputField()).isEqualTo("json");
    assertThat(meta.getOutputType()).isEqualTo(JsonOutputType.JSON);
    assertThat(meta.getMode()).isEqualTo(JsonBuilderMode.CREATE);
    assertThat(meta.isPrettyPrint()).isFalse();
    assertThat(meta.getMappings()).isEmpty();
    assertThat(meta.isGrouping()).isFalse();
  }

  @Test
  void xmlRoundTripPreservesConfiguration() throws Exception {
    JsonObjectBuilderMeta meta = new JsonObjectBuilderMeta();
    meta.setOutputField("item_json");
    meta.setOutputType(JsonOutputType.JSON);
    meta.setPrettyPrint(true);
    meta.setMode(JsonBuilderMode.INSERT);
    meta.setBaseJsonField("item_json");
    meta.setJsonPointer("/assets");

    JsonMapping mapping = new JsonMapping();
    mapping.setKeySource(JsonKeySource.FIELD);
    mapping.setKeyField("asset_key");
    mapping.setValueSource(JsonValueSource.FIELD);
    mapping.setValueField("asset_json");
    mapping.setValueType(JsonValueType.JSON);
    mapping.setSkipIfNull(true);
    meta.getMappings().add(mapping);

    JsonMapping literal = new JsonMapping();
    literal.setKeySource(JsonKeySource.LITERAL);
    literal.setKey("roles");
    literal.setValueSource(JsonValueSource.LITERAL);
    literal.setValue("[\"data\"]");
    literal.setValueType(JsonValueType.JSON);
    meta.getMappings().add(literal);

    meta.getGroupByFields().add("item_id");

    String xml = "<" + TransformMeta.XML_TAG + ">" + meta.getXml() + "</" + TransformMeta.XML_TAG + ">";

    JsonObjectBuilderMeta copy = new JsonObjectBuilderMeta();
    copy.loadXml(XmlHandler.loadXmlString(xml, TransformMeta.XML_TAG), null);

    assertThat(copy.getOutputField()).isEqualTo("item_json");
    assertThat(copy.getOutputType()).isEqualTo(JsonOutputType.JSON);
    assertThat(copy.isPrettyPrint()).isTrue();
    assertThat(copy.getMode()).isEqualTo(JsonBuilderMode.INSERT);
    assertThat(copy.getBaseJsonField()).isEqualTo("item_json");
    assertThat(copy.getJsonPointer()).isEqualTo("/assets");
    assertThat(copy.getGroupByFields()).containsExactly("item_id");
    assertThat(copy.getMappings()).hasSize(2);

    JsonMapping copiedMapping = copy.getMappings().get(0);
    assertThat(copiedMapping.getKeySource()).isEqualTo(JsonKeySource.FIELD);
    assertThat(copiedMapping.getKeyField()).isEqualTo("asset_key");
    assertThat(copiedMapping.getValueField()).isEqualTo("asset_json");
    assertThat(copiedMapping.getValueType()).isEqualTo(JsonValueType.JSON);
    assertThat(copiedMapping.isSkipIfNull()).isTrue();

    JsonMapping copiedLiteral = copy.getMappings().get(1);
    assertThat(copiedLiteral.getKey()).isEqualTo("roles");
    assertThat(copiedLiteral.getValue()).isEqualTo("[\"data\"]");
  }

  @Test
  void cloneCopiesMutableCollections() {
    JsonObjectBuilderMeta meta = new JsonObjectBuilderMeta();
    meta.getMappings().add(new JsonMapping());
    meta.getGroupByFields().add("item_id");

    JsonObjectBuilderMeta copy = (JsonObjectBuilderMeta) meta.clone();
    copy.getMappings().clear();
    copy.getGroupByFields().clear();

    assertThat(meta.getMappings()).hasSize(1);
    assertThat(meta.getGroupByFields()).containsExactly("item_id");
  }

  @Test
  void getFieldsAppendsTheOutputField() throws Exception {
    JsonObjectBuilderMeta meta = new JsonObjectBuilderMeta();
    meta.setOutputField("item_json");

    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("id"));

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.size()).isEqualTo(2);
    assertThat(rowMeta.getValueMeta(1).getName()).isEqualTo("item_json");
    assertThat(rowMeta.getValueMeta(1).getType()).isEqualTo(org.apache.hop.core.row.IValueMeta.TYPE_JSON);
  }

  @Test
  void getFieldsReplacesAnExistingFieldInPlace() throws Exception {
    JsonObjectBuilderMeta meta = new JsonObjectBuilderMeta();
    meta.setOutputField("item_json");

    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("id"));
    rowMeta.addValueMeta(new ValueMetaString("item_json"));

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.size()).isEqualTo(2);
    assertThat(rowMeta.getValueMeta(1).getName()).isEqualTo("item_json");
    assertThat(rowMeta.getValueMeta(1).getType()).isEqualTo(org.apache.hop.core.row.IValueMeta.TYPE_JSON);
  }

  @Test
  void getFieldsInGroupingModeKeepsOnlyGroupFieldsAndTheOutput() throws Exception {
    JsonObjectBuilderMeta meta = new JsonObjectBuilderMeta();
    meta.setOutputField("item_json");
    meta.getGroupByFields().add("item_id");

    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("item_id"));
    rowMeta.addValueMeta(new ValueMetaString("asset_key"));
    rowMeta.addValueMeta(new ValueMetaInteger("size"));

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.size()).isEqualTo(2);
    assertThat(rowMeta.getValueMeta(0).getName()).isEqualTo("item_id");
    assertThat(rowMeta.getValueMeta(1).getName()).isEqualTo("item_json");
  }

  @Test
  void validateRejectsUnknownValueField() {
    JsonObjectBuilderMeta meta = new JsonObjectBuilderMeta();
    meta.setOutputField("item_json");
    JsonMapping mapping = new JsonMapping();
    mapping.setKey("id");
    mapping.setValueSource(JsonValueSource.FIELD);
    mapping.setValueField("missing");
    meta.getMappings().add(mapping);

    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("id"));

    List<ICheckResult> remarks = new ArrayList<>();
    meta.check(remarks, null, null, rowMeta, new String[] {"upstream"}, new String[0], null, new Variables(), null);

    assertThat(remarks)
        .extracting(ICheckResult::getText)
        .anySatisfy(text -> assertThat(text).contains("value field 'missing' was not found"));
  }

  @Test
  void validateRejectsInvalidJsonPointer() {
    JsonObjectBuilderMeta meta = new JsonObjectBuilderMeta();
    meta.setOutputField("item_json");
    meta.setMode(JsonBuilderMode.INSERT);
    meta.setBaseJsonField("item_json");
    meta.setJsonPointer("assets/");

    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("item_json"));

    JsonMapping mapping = new JsonMapping();
    mapping.setKey("id");
    mapping.setValueSource(JsonValueSource.LITERAL);
    mapping.setValue("x");
    meta.getMappings().add(mapping);

    List<ICheckResult> remarks = new ArrayList<>();
    meta.check(remarks, null, null, rowMeta, new String[] {"upstream"}, new String[0], null, new Variables(), null);

    assertThat(remarks)
        .extracting(ICheckResult::getText)
        .anySatisfy(text -> assertThat(text).contains("Invalid JSON Pointer"));
  }

  @Test
  void validateRejectsInvalidLiteralValue() {
    JsonObjectBuilderMeta meta = new JsonObjectBuilderMeta();
    meta.setOutputField("item_json");
    JsonMapping mapping = new JsonMapping();
    mapping.setKey("count");
    mapping.setValueSource(JsonValueSource.LITERAL);
    mapping.setValue("abc");
    mapping.setValueType(JsonValueType.INTEGER);
    meta.getMappings().add(mapping);

    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("id"));

    List<ICheckResult> remarks = new ArrayList<>();
    meta.check(remarks, null, null, rowMeta, new String[] {"upstream"}, new String[0], null, new Variables(), null);

    assertThat(remarks)
        .extracting(ICheckResult::getText)
        .anySatisfy(text -> assertThat(text).contains("Mapping row 1"));
  }

  @Test
  void validateAcceptsAValidGroupedInsertConfiguration() {
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

    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("item_id"));
    rowMeta.addValueMeta(new ValueMetaString("asset_key"));
    rowMeta.addValueMeta(new ValueMetaJson("asset_json"));
    rowMeta.addValueMeta(new ValueMetaString("item_json"));

    assertThatCode(() -> meta.validate(rowMeta, new Variables())).doesNotThrowAnyException();
  }
}
