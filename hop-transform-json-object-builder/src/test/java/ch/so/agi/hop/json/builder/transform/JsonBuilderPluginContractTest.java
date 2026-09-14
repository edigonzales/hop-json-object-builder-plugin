package ch.so.agi.hop.json.builder.transform;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hop.core.annotations.Transform;
import org.junit.jupiter.api.Test;

class JsonBuilderPluginContractTest {

  @Test
  void objectBuilderIsRegisteredInTheJsonCategory() {
    Transform plugin = JsonObjectBuilderMeta.class.getAnnotation(Transform.class);

    assertThat(plugin).isNotNull();
    assertThat(plugin.id()).isEqualTo("JSON_OBJECT_BUILDER");
    assertThat(plugin.name()).isEqualTo("JSON Object Builder");
    assertThat(plugin.categoryDescription()).isEqualTo("JSON");
  }

  @Test
  void arrayBuilderIsRegisteredInTheJsonCategory() {
    Transform plugin = JsonArrayBuilderMeta.class.getAnnotation(Transform.class);

    assertThat(plugin).isNotNull();
    assertThat(plugin.id()).isEqualTo("JSON_ARRAY_BUILDER");
    assertThat(plugin.name()).isEqualTo("JSON Array Builder");
    assertThat(plugin.categoryDescription()).isEqualTo("JSON");
  }

  @Test
  void transformIconsArePackaged() {
    String objectIcon = JsonObjectBuilderMeta.class.getAnnotation(Transform.class).image();
    String arrayIcon = JsonArrayBuilderMeta.class.getAnnotation(Transform.class).image();

    assertThat(getClass().getResource("/" + objectIcon)).isNotNull();
    assertThat(getClass().getResource("/" + arrayIcon)).isNotNull();
  }

  @Test
  void dialogClassesAreWired() {
    assertThat(new JsonObjectBuilderMeta().getDialogClassName())
        .isEqualTo(JsonObjectBuilderDialog.class.getName());
    assertThat(new JsonArrayBuilderMeta().getDialogClassName())
        .isEqualTo(JsonArrayBuilderDialog.class.getName());
  }
}
