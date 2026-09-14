package ch.so.agi.hop.json.builder.transform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Loads the shipped example pipelines with Hop to keep them in sync with the transform XML. */
class JsonBuilderExamplesTest {

  @BeforeEach
  void initHop() throws Exception {
    HopEnvironment.init();
  }

  @AfterEach
  void resetHop() {
    HopEnvironment.reset();
  }

  @Test
  void stacItemExampleLoadsAndTransformsAreConnected() throws Exception {
    PipelineMeta pipeline = loadExample("stac-item", "stac-item");

    assertThat(pipeline.getTransforms()).hasSize(7);
    assertThat(hopFromTo(pipeline, "Read STAC metadata", "Sort by item")).isTrue();
    assertThat(hopFromTo(pipeline, "Assets", "Write STAC item")).isTrue();
    assertThat(pipeline.findTransform("Assets").getTypeId()).isEqualTo("JSON_OBJECT_BUILDER");
    assertThat(pipeline.findTransform("Write STAC item").getTypeId()).isEqualTo("TextFileOutput");

    JsonObjectBuilderMeta itemBase =
        (JsonObjectBuilderMeta) pipeline.findTransform("Item base").getTransform();
    assertThat(itemBase.getMode()).isEqualTo(JsonBuilderMode.CREATE);
    assertThat(itemBase.getOutputField()).isEqualTo("item_json");
    assertThat(itemBase.getMappings()).hasSize(6);

    JsonObjectBuilderMeta assets =
        (JsonObjectBuilderMeta) pipeline.findTransform("Assets").getTransform();
    assertThat(assets.getMode()).isEqualTo(JsonBuilderMode.INSERT);
    assertThat(assets.getJsonPointer()).isEqualTo("/assets");
    assertThat(assets.isGrouping()).isTrue();
    assertThat(assets.getGroupByFields()).containsExactly("item_id");
    assertThat(assets.isPrettyPrint()).isTrue();
    assertThat(assets.getMappings()).hasSize(1);
    assertThat(assets.getMappings().get(0).getKeySource()).isEqualTo(JsonKeySource.FIELD);
    assertThat(assets.getMappings().get(0).getKeyField()).isEqualTo("asset_key");
    assertThat(assets.getMappings().get(0).getValueSource()).isEqualTo(JsonValueSource.FIELD);
    assertThat(assets.getMappings().get(0).getValueField()).isEqualTo("asset_json");
  }

  @Test
  void linksArrayExampleLoadsWithSkipWhenNullMapping() throws Exception {
    PipelineMeta pipeline = loadExample("links-array", "links-array");

    assertThat(hopFromTo(pipeline, "Link object", "Links array")).isTrue();
    assertThat(pipeline.findTransform("Links array").getTypeId()).isEqualTo("JSON_ARRAY_BUILDER");

    JsonObjectBuilderMeta linkObject =
        (JsonObjectBuilderMeta) pipeline.findTransform("Link object").getTransform();
    assertThat(linkObject.getMappings()).hasSize(3);
    assertThat(linkObject.getMappings().get(2).isSkipIfNull()).isTrue();

    JsonArrayBuilderMeta links =
        (JsonArrayBuilderMeta) pipeline.findTransform("Links array").getTransform();
    assertThat(links.getElementMode()).isEqualTo(JsonElementMode.FIELD_VALUE);
    assertThat(links.getElementField()).isEqualTo("link_json");
    assertThat(links.getGroupByFields()).containsExactly("item_id");
    assertThat(links.isPrettyPrint()).isTrue();
    assertThat(links.isInsertIntoTarget()).isFalse();
  }

  private PipelineMeta loadExample(String directory, String name) throws Exception {
    Path file = Path.of("..", "examples", directory, name + ".hpl");
    assertThat(Files.isRegularFile(file)).as("example pipeline " + file).isTrue();
    try (InputStream stream = Files.newInputStream(file)) {
      return new PipelineMeta(stream, new MemoryMetadataProvider(), new Variables());
    }
  }

  private boolean hopFromTo(PipelineMeta pipeline, String from, String to) {
    TransformMeta fromMeta = pipeline.findTransform(from);
    if (fromMeta == null) {
      return false;
    }
    return pipeline.findNextTransforms(fromMeta).stream()
        .anyMatch(target -> to.equals(target.getName()));
  }
}
