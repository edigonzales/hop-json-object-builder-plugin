package ch.so.agi.hop.json.builder.transform;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.commons.core.SourceMode;
import ch.so.agi.hop.commons.ui.ValueOrFieldControl;
import ch.so.agi.hop.json.builder.core.JsonValueType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.history.AuditManager;
import org.apache.hop.history.IAuditManager;
import org.apache.hop.history.local.LocalAuditManager;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonBuilderDialogTest {
  private static Display display;
  @TempDir static Path auditFolder;
  private static IAuditManager originalAuditManager;

  @BeforeAll
  static void init() throws Exception {
    HopEnvironment.init();
    originalAuditManager = AuditManager.getInstance().getActiveAuditManager();
    AuditManager.getInstance().setActiveAuditManager(new LocalAuditManager(auditFolder.toString()));
    display = Display.getDefault();
  }

  @AfterAll
  static void close() {
    display.dispose();
    AuditManager.getInstance().setActiveAuditManager(originalAuditManager);
    HopEnvironment.reset();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void failedSaveThenCancelPreservesConfigurationAndChangedFlag(boolean object) throws Exception {
    for (boolean initiallyChanged : new boolean[] {false, true}) {
      Fixture fixture = fixture(object);
      fixture.meta.setChanged(initiallyChanged);
      String before = fixture.meta.getXml();
      exercise(
          fixture,
          () -> {
            text(fixture.dialog, "wTransformName").setText("renamed");
            text(fixture.dialog, "wOutputField").setText("changed_output");
            if (object) {
              ValueOrFieldControl value = editor(fixture, "wMappingValue");
              ((Combo) field(value, "field")).setText("absent");
            } else {
              ((Combo) field(fixture.dialog, "wElementField")).setText("absent");
            }
            invoke(fixture.dialog, "ok");
            assertThat(fixture.warning()).contains("absent");
            assertThat(fixture.meta.getXml()).isEqualTo(before);
            invoke(fixture.dialog, "cancel");
          });
      assertThat(fixture.meta.getXml()).isEqualTo(before);
      assertThat(fixture.meta.hasChanged()).isEqualTo(initiallyChanged);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void successfulSaveCommitsConfigurationAndRename(boolean object) throws Exception {
    Fixture fixture = fixture(object);
    fixture.meta.setChanged(false);
    String result =
        exercise(
            fixture,
            () -> {
              text(fixture.dialog, "wTransformName").setText("renamed");
              text(fixture.dialog, "wOutputField").setText("result");
              invoke(fixture.dialog, "ok");
              assertThat(fixture.warning()).isNull();
            });
    assertThat(result).isEqualTo("renamed");
    assertThat(fixture.meta.hasChanged()).isTrue();
    assertThat(
            object
                ? ((JsonObjectBuilderMeta) fixture.meta).getOutputField()
                : ((JsonArrayBuilderMeta) fixture.meta).getOutputField())
        .isEqualTo("result");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void committingConfigurationDoesNotShareMutableLists(boolean object) {
    if (object) {
      JsonObjectBuilderMeta candidate = JsonBuilderRegressionTest.objectMeta();
      candidate.getGroupByFields().add("value");
      JsonObjectBuilderMeta target = new JsonObjectBuilderMeta();
      target.copyConfigurationFrom(candidate);
      candidate.getMappings().getFirst().setKey("changed");
      candidate.getGroupByFields().clear();
      assertThat(target.getMappings().getFirst().getKey()).isEqualTo("value");
      assertThat(target.getGroupByFields()).containsExactly("value");
    } else {
      JsonArrayBuilderMeta candidate = JsonBuilderRegressionTest.arrayMeta();
      candidate.getGroupByFields().add("value");
      JsonArrayBuilderMeta target = new JsonArrayBuilderMeta();
      target.copyConfigurationFrom(candidate);
      candidate.getGroupByFields().clear();
      assertThat(target.getGroupByFields()).containsExactly("value");
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void mappingsKeepBothInputsAcrossRowsAndModesAndCommitOnlyOnOk(boolean save) throws Exception {
    Fixture fixture = fixture(true);
    JsonObjectBuilderMeta meta = (JsonObjectBuilderMeta) fixture.meta;
    String before = meta.getXml();
    String result =
        exercise(
            fixture,
            () -> {
              ValueOrFieldControl key = editor(fixture, "wMappingKey");
              ValueOrFieldControl value = editor(fixture, "wMappingValue");
              ((TextVar) field(key, "configured")).setText("fallback_key");
              selectSource(key, 1);
              Combo keyField = (Combo) field(key, "field");
              assertThat(keyField.getItems()).contains("value");
              keyField.setText("value");
              selectSource(value, 0);
              ((TextVar) field(value, "configured")).setText("fallback_value");
              selectSource(value, 1);
              ((Combo) field(value, "field")).setText("value");

              Button remove = (Button) field(fixture.dialog, "wRemoveMapping");
              Button add =
                  Arrays.stream(remove.getParent().getChildren())
                      .filter(Button.class::isInstance)
                      .map(Button.class::cast)
                      .filter(button -> button.getText().equals("Add"))
                      .findFirst()
                      .orElseThrow();
              add.notifyListeners(SWT.Selection, new Event());
              ((TextVar) field(key, "configured")).setText("count");
              selectSource(value, 0);
              ((TextVar) field(value, "configured")).setText("12");
              Combo type = (Combo) field(fixture.dialog, "wMappingType");
              type.setText(JsonValueType.NUMBER.getDescription());
              type.notifyListeners(SWT.Selection, new Event());
              Button skip = (Button) field(fixture.dialog, "wSkipWhenNull");
              skip.setSelection(true);
              skip.notifyListeners(SWT.Selection, new Event());
              ((Button) field(fixture.dialog, "wMoveMappingUp"))
                  .notifyListeners(SWT.Selection, new Event());

              add.notifyListeners(SWT.Selection, new Event());
              remove.notifyListeners(SWT.Selection, new Event());
              Table mappings = (Table) field(fixture.dialog, "wMappings");
              assertThat(mappings.getItemCount()).isEqualTo(2);
              assertThat(mappings.getItem(0).getText(0)).isEqualTo("count");

              mappings.setSelection(1);
              mappings.notifyListeners(SWT.Selection, new Event());
              selectSource(key, 0);
              selectSource(value, 0);
              assertThat(key.getValue().configuredValue()).isEqualTo("fallback_key");
              assertThat(value.getValue().configuredValue()).isEqualTo("fallback_value");
              selectSource(key, 1);
              selectSource(value, 1);
              assertThat(key.getValue().fieldName()).isEqualTo("value");
              assertThat(value.getValue().fieldName()).isEqualTo("value");
              assertThat(meta.getXml()).isEqualTo(before);
              invoke(fixture.dialog, save ? "ok" : "cancel");
              assertThat(fixture.warning()).isNull();
            });

    if (!save) {
      assertThat(result).isNull();
      assertThat(meta.getXml()).isEqualTo(before);
      return;
    }
    assertThat(result).isEqualTo("builder");
    var loaded = new JsonObjectBuilderMeta();
    loaded.loadXml(XmlHandler.loadXmlString("<transform>" + meta.getXml() + "</transform>", "transform"), null);
    assertThat(loaded.getMappings()).hasSize(2);
    JsonMapping literal = loaded.getMappings().get(0);
    assertThat(literal.getKey()).isEqualTo("count");
    assertThat(literal.getValue()).isEqualTo("12");
    assertThat(literal.getValueSource()).isEqualTo(SourceMode.CONFIGURED);
    assertThat(literal.getValueType()).isEqualTo(JsonValueType.NUMBER);
    assertThat(literal.isSkipIfNull()).isTrue();
    JsonMapping dynamic = loaded.getMappings().get(1);
    assertThat(dynamic.getKeySource()).isEqualTo(SourceMode.FIELD);
    assertThat(dynamic.getValueSource()).isEqualTo(SourceMode.FIELD);
    assertThat(dynamic.getKeyField()).isEqualTo("value");
    assertThat(dynamic.getValueField()).isEqualTo("value");
    assertThat(dynamic.getKey()).isEqualTo("fallback_key");
    assertThat(dynamic.getValue()).isEqualTo("fallback_value");
  }

  private static ValueOrFieldControl editor(Fixture fixture, String name) throws Exception {
    return (ValueOrFieldControl) field(fixture.dialog, name);
  }

  private static void selectSource(ValueOrFieldControl control, int index) throws Exception {
    Combo source = (Combo) field(control, "source");
    source.select(index);
    source.notifyListeners(SWT.Selection, new Event());
  }

  private Fixture fixture(boolean object) {
    PipelineMeta pipeline = new PipelineMeta();
    JsonObjectBuilderMeta upstream = JsonBuilderRegressionTest.objectMeta();
    upstream.setOutputField("value");
    TransformMeta source = new TransformMeta("source", upstream);
    BaseTransformMeta<?, ?> meta =
        object ? JsonBuilderRegressionTest.objectMeta() : JsonBuilderRegressionTest.arrayMeta();
    TransformMeta target = new TransformMeta("builder", meta);
    pipeline.addTransform(source);
    pipeline.addTransform(target);
    pipeline.addPipelineHop(new PipelineHopMeta(source, target));
    Shell parent = new Shell(display);
    BaseTransformDialog dialog =
        object
            ? new ObjectDialog(parent, (JsonObjectBuilderMeta) meta, pipeline)
            : new ArrayDialog(parent, (JsonArrayBuilderMeta) meta, pipeline);
    return new Fixture(meta, dialog, parent);
  }

  private String exercise(Fixture fixture, CheckedAction action) throws Exception {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    display.asyncExec(
        () -> {
          try {
            action.run();
          } catch (Throwable error) {
            failure.set(error);
            try {
              ((Shell) field(fixture.dialog, "shell")).dispose();
            } catch (Exception ignored) {
              fixture.parent.dispose();
            }
          }
        });
    String result;
    try {
      result = fixture.dialog.open();
    } finally {
      fixture.parent.dispose();
    }
    if (failure.get() != null) {
      throw new AssertionError("Dialog interaction failed", failure.get());
    }
    return result;
  }

  private static Text text(Object target, String name) throws Exception {
    return (Text) field(target, name);
  }

  private static Object field(Object target, String name) throws Exception {
    for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
      try {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
      } catch (NoSuchFieldException ignored) {
      }
    }
    throw new NoSuchFieldException(name);
  }

  private static void invoke(Object target, String name) throws Exception {
    Method method = target.getClass().getSuperclass().getDeclaredMethod(name);
    method.setAccessible(true);
    method.invoke(target);
  }

  interface CheckedAction {
    void run() throws Exception;
  }

  record Fixture(BaseTransformMeta<?, ?> meta, BaseTransformDialog dialog, Shell parent) {
    String warning() {
      return dialog instanceof ObjectDialog object
          ? object.warning
          : ((ArrayDialog) dialog).warning;
    }
  }

  static class ObjectDialog extends JsonObjectBuilderDialog {
    String warning;

    ObjectDialog(Shell shell, JsonObjectBuilderMeta meta, PipelineMeta pipeline) {
      super(shell, new Variables(), meta, pipeline);
    }

    @Override
    void showWarning(String message) {
      warning = message;
    }
  }

  static class ArrayDialog extends JsonArrayBuilderDialog {
    String warning;

    ArrayDialog(Shell shell, JsonArrayBuilderMeta meta, PipelineMeta pipeline) {
      super(shell, new Variables(), meta, pipeline);
    }

    @Override
    void showWarning(String message) {
      warning = message;
    }
  }
}
