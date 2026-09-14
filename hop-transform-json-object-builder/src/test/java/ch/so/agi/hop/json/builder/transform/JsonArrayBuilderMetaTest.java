package ch.so.agi.hop.json.builder.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.so.agi.hop.json.builder.core.JsonValueType;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonArrayBuilderMetaTest {

  @BeforeEach
  void initHop() throws Exception {
    HopEnvironment.init();
  }

  @AfterEach
  void resetHop() {
    HopEnvironment.reset();
  }

  @Test
  void defaultsCollectAllRowsIntoOneArray() {
    JsonArrayBuilderMeta meta = new JsonArrayBuilderMeta();

    assertThat(meta.getOutputField()).isEqualTo("array");
    assertThat(meta.getOutputType()).isEqualTo(JsonOutputType.JSON);
    assertThat(meta.getElementMode()).isEqualTo(JsonElementMode.FIELD_VALUE);
    assertThat(meta.getElementType()).isEqualTo(JsonValueType.AUTO);
    assertThat(meta.isInsertIntoTarget()).isFalse();
    assertThat(meta.isGrouping()).isFalse();
  }

  @Test
  void xmlRoundTripPreservesConfiguration() throws Exception {
    JsonArrayBuilderMeta meta = new JsonArrayBuilderMeta();
    meta.setOutputField("links_json");
    meta.setOutputType(JsonOutputType.STRING);
    meta.setPrettyPrint(true);
    meta.setElementMode(JsonElementMode.FIELD_VALUE);
    meta.setElementField("link_json");
    meta.setElementType(JsonValueType.JSON);
    meta.setInsertIntoTarget(true);
    meta.setBaseJsonField("item_json");
    meta.setJsonPointer("/links");
    meta.getGroupByFields().add("item_id");
    meta.setSkipNullElements(true);

    String xml = "<" + TransformMeta.XML_TAG + ">" + meta.getXml() + "</" + TransformMeta.XML_TAG + ">";

    JsonArrayBuilderMeta copy = new JsonArrayBuilderMeta();
    copy.loadXml(XmlHandler.loadXmlString(xml, TransformMeta.XML_TAG), null);

    assertThat(copy.getOutputField()).isEqualTo("links_json");
    assertThat(copy.getOutputType()).isEqualTo(JsonOutputType.STRING);
    assertThat(copy.isPrettyPrint()).isTrue();
    assertThat(copy.getElementField()).isEqualTo("link_json");
    assertThat(copy.getElementType()).isEqualTo(JsonValueType.JSON);
    assertThat(copy.isInsertIntoTarget()).isTrue();
    assertThat(copy.getBaseJsonField()).isEqualTo("item_json");
    assertThat(copy.getJsonPointer()).isEqualTo("/links");
    assertThat(copy.getGroupByFields()).containsExactly("item_id");
    assertThat(copy.isSkipNullElements()).isTrue();
  }

  @Test
  void cloneCopiesMutableCollections() {
    JsonArrayBuilderMeta meta = new JsonArrayBuilderMeta();
    meta.getGroupByFields().add("item_id");

    JsonArrayBuilderMeta copy = (JsonArrayBuilderMeta) meta.clone();
    copy.getGroupByFields().clear();

    assertThat(meta.getGroupByFields()).containsExactly("item_id");
  }

  @Test
  void getFieldsWithoutGroupingKeepsOnlyTheOutputField() throws Exception {
    JsonArrayBuilderMeta meta = new JsonArrayBuilderMeta();
    meta.setOutputField("links_json");

    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("item_id"));
    rowMeta.addValueMeta(new ValueMetaString("href"));

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.size()).isEqualTo(1);
    assertThat(rowMeta.getValueMeta(0).getName()).isEqualTo("links_json");
    assertThat(rowMeta.getValueMeta(0).getType()).isEqualTo(IValueMeta.TYPE_JSON);
  }

  @Test
  void getFieldsWithGroupingKeepsGroupFieldsAndTheOutput() throws Exception {
    JsonArrayBuilderMeta meta = new JsonArrayBuilderMeta();
    meta.setOutputField("links_json");
    meta.getGroupByFields().add("item_id");

    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("item_id"));
    rowMeta.addValueMeta(new ValueMetaString("href"));

    meta.getFields(rowMeta, "origin", null, null, new Variables(), null);

    assertThat(rowMeta.size()).isEqualTo(2);
    assertThat(rowMeta.getValueMeta(0).getName()).isEqualTo("item_id");
    assertThat(rowMeta.getValueMeta(1).getName()).isEqualTo("links_json");
  }

  @Test
  void validateRejectsMissingElementField() {
    JsonArrayBuilderMeta meta = new JsonArrayBuilderMeta();
    meta.setOutputField("array");
    meta.setElementField("missing");

    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaInteger("id"));

    List<ICheckResult> remarks = new ArrayList<>();
    meta.check(remarks, null, null, rowMeta, new String[] {"upstream"}, new String[0], null, new Variables(), null);

    assertThat(remarks)
        .extracting(ICheckResult::getText)
        .anySatisfy(text -> assertThat(text).contains("Element field 'missing' was not found"));
  }

  @Test
  void validateRejectsEmptyPointerWhenInserting() {
    JsonArrayBuilderMeta meta = new JsonArrayBuilderMeta();
    meta.setOutputField("array");
    meta.setElementField("id");
    meta.setInsertIntoTarget(true);
    meta.setBaseJsonField("item_json");
    meta.setJsonPointer("");

    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaInteger("id"));
    rowMeta.addValueMeta(new ValueMetaString("item_json"));

    List<ICheckResult> remarks = new ArrayList<>();
    meta.check(remarks, null, null, rowMeta, new String[] {"upstream"}, new String[0], null, new Variables(), null);

    assertThat(remarks)
        .extracting(ICheckResult::getText)
        .anySatisfy(text -> assertThat(text).contains("JSON Pointer is missing"));
  }

  @Test
  void validateAcceptsAValidGroupedConfiguration() {
    JsonArrayBuilderMeta meta = new JsonArrayBuilderMeta();
    meta.setOutputField("links_json");
    meta.setElementField("link_json");
    meta.setElementType(JsonValueType.JSON);
    meta.getGroupByFields().add("item_id");

    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("item_id"));
    rowMeta.addValueMeta(new ValueMetaString("link_json"));

    assertThatCode(() -> meta.validate(rowMeta, new Variables())).doesNotThrowAnyException();
  }
}
