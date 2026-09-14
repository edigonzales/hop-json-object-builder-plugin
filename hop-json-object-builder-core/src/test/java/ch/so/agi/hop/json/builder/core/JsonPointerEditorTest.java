package ch.so.agi.hop.json.builder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class JsonPointerEditorTest {

  @Test
  void setValueCreatesMissingObjectLevels() {
    ObjectNode root = JsonSupport.NODES.objectNode();

    JsonPointerEditor.setValue(
        root, JsonPointerEditor.compile("/properties"), "datetime", JsonSupport.NODES.textNode("now"));

    assertThat(root.toString()).isEqualTo("{\"properties\":{\"datetime\":\"now\"}}");
  }

  @Test
  void setValueAtRootUsesTheRootObject() {
    ObjectNode root = JsonSupport.NODES.objectNode();

    JsonPointerEditor.setValue(
        root, JsonPointerEditor.compile(""), "id", JsonSupport.NODES.textNode("abc"));

    assertThat(root.toString()).isEqualTo("{\"id\":\"abc\"}");
  }

  @Test
  void setValueOverwritesExistingValue() {
    ObjectNode root = (ObjectNode) parse("{\"properties\":{\"datetime\":\"old\"}}");

    JsonPointerEditor.setValue(
        root, JsonPointerEditor.compile("/properties"), "datetime", JsonSupport.NODES.textNode("new"));

    assertThat(root.toString()).isEqualTo("{\"properties\":{\"datetime\":\"new\"}}");
  }

  @Test
  void setValueKeepsDynamicKeysInInsertionOrder() {
    ObjectNode root = (ObjectNode) parse("{\"assets\":{}}");
    JsonPointer pointer = JsonPointerEditor.compile("/assets");

    JsonPointerEditor.setValue(root, pointer, "geoparquet", parse("{\"href\":\"a.parquet\"}"));
    JsonPointerEditor.setValue(root, pointer, "interlis", parse("{\"href\":\"a.xtf\"}"));

    assertThat(root.toString())
        .isEqualTo("{\"assets\":{\"geoparquet\":{\"href\":\"a.parquet\"},\"interlis\":{\"href\":\"a.xtf\"}}}");
  }

  @Test
  void resolveNodeNavigatesExistingArrayElements() {
    JsonNode root = parse("{\"links\":[{\"rel\":\"self\"},{\"rel\":\"root\"}]}");

    JsonNode node = JsonPointerEditor.resolveNode(root, JsonPointerEditor.compile("/links/1/rel"));

    assertThat(node.asText()).isEqualTo("root");
  }

  @Test
  void resolveObjectRejectsNavigationThroughScalars() {
    JsonNode root = parse("{\"a\":1}");

    assertThatThrownBy(() -> JsonPointerEditor.resolveObject(root, JsonPointerEditor.compile("/a/b")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot navigate JSON Pointer token");
  }

  @Test
  void setValueEscapesJsonPointerTokens() {
    ObjectNode root = JsonSupport.NODES.objectNode();

    JsonPointerEditor.setValue(
        root, JsonPointerEditor.compile("/a~1b"), "c~0d", JsonSupport.NODES.textNode("v"));

    // Pointer tokens are escaped in the pointer ("a~1b" -> key "a/b"); mapping keys are raw keys.
    assertThat(root.toString()).isEqualTo("{\"a/b\":{\"c~0d\":\"v\"}}");
  }

  @Test
  void setArrayCreatesTheArrayUnderTheLastToken() {
    ObjectNode root = JsonSupport.NODES.objectNode();

    JsonPointerEditor.setArray(
        root, JsonPointerEditor.compile("/links"), JsonSupport.NODES.arrayNode().add("a"));

    assertThat(root.toString()).isEqualTo("{\"links\":[\"a\"]}");
  }

  @Test
  void setArrayAppendsToAnExistingArray() {
    ObjectNode root = (ObjectNode) parse("{\"links\":[\"a\"]}");

    JsonPointerEditor.setArray(
        root, JsonPointerEditor.compile("/links/-"), JsonSupport.NODES.arrayNode().add("b"));

    assertThat(root.toString()).isEqualTo("{\"links\":[\"a\",[\"b\"]]}");
  }

  @Test
  void setArrayRejectsEmptyPointers() {
    ObjectNode root = JsonSupport.NODES.objectNode();

    assertThatThrownBy(
            () ->
                JsonPointerEditor.setArray(
                    root, JsonPointerEditor.compile(""), JsonSupport.NODES.arrayNode()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("array insertion is empty");
  }

  @Test
  void resolveObjectRejectsNullBase() {
    assertThatThrownBy(
            () -> JsonPointerEditor.resolveObject(null, JsonPointerEditor.compile("/a")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Base JSON value is null");
  }

  private JsonNode parse(String text) {
    try {
      return JsonSupport.parse(text);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
