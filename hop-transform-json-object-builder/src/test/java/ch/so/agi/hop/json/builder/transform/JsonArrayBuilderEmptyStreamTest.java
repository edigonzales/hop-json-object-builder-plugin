package ch.so.agi.hop.json.builder.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.BlockingRowSet;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engines.local.LocalPipelineEngine;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class JsonArrayBuilderEmptyStreamTest {
  @BeforeEach
  void init() throws Exception {
    HopEnvironment.init();
  }

  @AfterEach
  void reset() {
    HopEnvironment.reset();
  }

  @ParameterizedTest
  @EnumSource(JsonOutputType.class)
  void realEmptyInputProducesOneEmptyArray(JsonOutputType type) throws Exception {
    JsonArrayBuilderMeta meta = JsonBuilderRegressionTest.arrayMeta();
    meta.setOutputType(type);
    EmptyRunner runner = runner(meta);
    try {
      assertThat(runner.getInputRowMeta()).isNull();
      assertThat(runner.processRow()).isFalse();
      assertThat(runner.output).hasSize(1);
      if (type == JsonOutputType.STRING) {
        assertThat(runner.output.getFirst()[0]).isEqualTo("[]");
      } else {
        assertThat((JsonNode) runner.output.getFirst()[0]).isEmpty();
      }
    } finally {
      runner.dispose();
    }
  }

  @Test
  void emptyInsertRequiresBaseUnlessGrouped() throws Exception {
    for (boolean grouped : List.of(false, true)) {
      JsonArrayBuilderMeta meta = JsonBuilderRegressionTest.arrayMeta();
      meta.setInsertIntoTarget(true);
      meta.setBaseJsonField("value");
      meta.setJsonPointer("/items");
      if (grouped) {
        meta.getGroupByFields().add("value");
      }
      EmptyRunner runner = runner(meta);
      try {
        if (grouped) {
          assertThat(runner.processRow()).isFalse();
        } else {
          assertThatThrownBy(runner::processRow).hasMessageContaining("No base JSON document");
        }
        assertThat(runner.output).isEmpty();
      } finally {
        runner.dispose();
      }
    }
  }

  @Test
  void emptyGroupedCreateDoesNotInventAGroup() throws Exception {
    JsonArrayBuilderMeta meta = JsonBuilderRegressionTest.arrayMeta();
    meta.getGroupByFields().add("value");
    EmptyRunner runner = runner(meta);
    try {
      assertThat(runner.processRow()).isFalse();
      assertThat(runner.output).isEmpty();
    } finally {
      runner.dispose();
    }
  }

  @Test
  void emptyInputStillValidatesConfiguration() {
    JsonArrayBuilderMeta meta = JsonBuilderRegressionTest.arrayMeta();
    meta.setElementField("absent");
    EmptyRunner runner = runner(meta);
    try {
      assertThatThrownBy(runner::processRow).hasMessageContaining("absent");
    } finally {
      runner.dispose();
    }
  }

  private EmptyRunner runner(JsonArrayBuilderMeta meta) {
    PipelineMeta pipeline = new PipelineMeta();
    JsonObjectBuilderMeta upstream = JsonBuilderRegressionTest.objectMeta();
    upstream.setOutputField("value");
    upstream.setOutputType(JsonOutputType.STRING);
    TransformMeta source = new TransformMeta("source", upstream);
    TransformMeta target = new TransformMeta("array", meta);
    pipeline.addTransform(source);
    pipeline.addTransform(target);
    pipeline.addPipelineHop(new PipelineHopMeta(source, target));
    LocalPipelineEngine engine = new LocalPipelineEngine();
    engine.setRunning(true);
    EmptyRunner runner = new EmptyRunner(target, meta, pipeline, engine);
    BlockingRowSet input = new BlockingRowSet(10);
    input.setThreadNameFromToCopy("source", 0, "array", 0);
    input.setDone();
    runner.addRowSetToInputRowSets(input);
    return runner;
  }

  private static class EmptyRunner extends JsonArrayBuilder {
    final List<Object[]> output = new ArrayList<>();

    EmptyRunner(
        TransformMeta target,
        JsonArrayBuilderMeta meta,
        PipelineMeta pipeline,
        LocalPipelineEngine engine) {
      super(target, meta, new JsonArrayBuilderData(), 0, pipeline, engine);
    }

    @Override
    public void dispatch() {}

    @Override
    public void putRow(IRowMeta meta, Object[] row) {
      output.add(row);
    }
  }
}
