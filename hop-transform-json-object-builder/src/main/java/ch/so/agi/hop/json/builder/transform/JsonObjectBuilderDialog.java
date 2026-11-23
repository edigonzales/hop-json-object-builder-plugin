package ch.so.agi.hop.json.builder.transform;

import ch.so.agi.hop.commons.core.SourceMode;
import ch.so.agi.hop.commons.core.ValueOrField;
import ch.so.agi.hop.commons.ui.EditorKind;
import ch.so.agi.hop.commons.ui.ValueOrFieldControl;
import ch.so.agi.hop.json.builder.core.JsonValueType;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

public class JsonObjectBuilderDialog extends BaseTransformDialog {

  private final JsonObjectBuilderMeta input;

  private Text wOutputField;
  private Combo wOutputType;
  private Button wPrettyPrint;
  private Button wModeCreate;
  private Button wModeInsert;
  private Combo wBaseJsonField;
  private Text wJsonPointer;
  private Label wlBaseJsonField;
  private Label wlJsonPointer;
  private Table wMappings;
  private ValueOrFieldControl wMappingKey;
  private ValueOrFieldControl wMappingValue;
  private Combo wMappingType;
  private Button wSkipWhenNull;
  private Button wRemoveMapping;
  private Button wMoveMappingUp;
  private Button wMoveMappingDown;
  private TableView wGroupBy;
  private final TransformMeta originalTransform;

  private String[] fieldNames = new String[0];

  public JsonObjectBuilderDialog(
      Shell parent,
      IVariables variables,
      JsonObjectBuilderMeta transformMeta,
      PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    this.input = transformMeta;
    this.originalTransform = this.transformMeta;
  }

  @Override
  public String open() {
    createShell("JSON Object Builder");

    buildButtonBar().ok(e -> ok()).cancel(e -> cancel()).build();

    changed = input.hasChanged();
    loadFieldNames();

    Composite content = new Composite(shell, SWT.NONE);
    PropsUi.setLook(content);
    GridLayout contentLayout = new GridLayout(2, false);
    contentLayout.marginWidth = 0;
    contentLayout.marginHeight = 0;
    contentLayout.horizontalSpacing = margin;
    contentLayout.verticalSpacing = margin;
    content.setLayout(contentLayout);
    FormData fdContent = new FormData();
    fdContent.left = new FormAttachment(0, 0);
    fdContent.right = new FormAttachment(100, 0);
    fdContent.top = new FormAttachment(wSpacer, margin * 2);
    fdContent.bottom = new FormAttachment(wOk, -margin * 2);
    content.setLayoutData(fdContent);

    addLabel(content, "Output field");
    wOutputField = new Text(content, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    wOutputField.setLayoutData(fill());
    wOutputField.addModifyListener(lsMod);

    addLabel(content, "Output type");
    wOutputType = new Combo(content, SWT.DROP_DOWN | SWT.READ_ONLY | SWT.BORDER);
    wOutputType.setItems(JsonOutputType.descriptions());
    wOutputType.setLayoutData(fill());
    wOutputType.addModifyListener(lsMod);

    addLabel(content, "");
    wPrettyPrint = new Button(content, SWT.CHECK);
    wPrettyPrint.setText("Pretty print");
    PropsUi.setLook(wPrettyPrint);
    wPrettyPrint.addListener(SWT.Selection, e -> markChanged());

    addLabel(content, "Source");
    Composite modeComposite = new Composite(content, SWT.NONE);
    modeComposite.setLayoutData(fill());
    GridLayout modeLayout = new GridLayout(1, false);
    modeLayout.marginWidth = 0;
    modeLayout.marginHeight = 0;
    modeComposite.setLayout(modeLayout);
    wModeCreate = new Button(modeComposite, SWT.RADIO);
    wModeCreate.setText(JsonBuilderMode.CREATE.getDescription());
    wModeInsert = new Button(modeComposite, SWT.RADIO);
    wModeInsert.setText(JsonBuilderMode.INSERT.getDescription());
    wModeCreate.addListener(SWT.Selection, e -> updateModeEnablement());
    wModeInsert.addListener(SWT.Selection, e -> updateModeEnablement());

    wlBaseJsonField = addLabel(content, "Base JSON field");
    wBaseJsonField = new Combo(content, SWT.DROP_DOWN | SWT.BORDER);
    wBaseJsonField.setItems(fieldNames);
    wBaseJsonField.setLayoutData(fill());
    wBaseJsonField.addModifyListener(lsMod);

    wlJsonPointer = addLabel(content, "JSON Pointer");
    wJsonPointer = new Text(content, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    wJsonPointer.setLayoutData(fill());
    wJsonPointer.addModifyListener(lsMod);

    createMappingEditor(content);

    Label wlGroupBy = new Label(content, SWT.NONE);
    wlGroupBy.setText("Group by fields (optional, merges rows into one JSON object per group):");
    wlGroupBy.setLayoutData(span(2));
    PropsUi.setLook(wlGroupBy);

    wGroupBy =
        new TableView(
            variables,
            content,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            new ColumnInfo[] {
              new ColumnInfo("Field", ColumnInfo.COLUMN_TYPE_CCOMBO, fieldNames, false)
            },
            Math.max(1, input.getGroupByFields().size()),
            lsMod,
            props);
    GridData groupData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
    groupData.heightHint = 90;
    wGroupBy.setLayoutData(groupData);

    Label wStatus = new Label(content, SWT.WRAP);
    wStatus.setText(
        "Insert mode creates missing object levels along the JSON Pointer (RFC 6901). "
            + "Grouped input must be sorted by the group by fields.");
    wStatus.setLayoutData(span(2));
    PropsUi.setLook(wStatus);

    getData();
    focusTransformName();
    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());
    return transformName;
  }

  private void createMappingEditor(Composite content) {
    Label label = new Label(content, SWT.NONE);
    label.setText("Field mappings");
    label.setLayoutData(span(2));
    PropsUi.setLook(label);

    wMappings = new Table(content, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
    wMappings.setHeaderVisible(true);
    wMappings.setLinesVisible(true);
    String[] headers = {"Key", "Value", "Type", "Skip when null"};
    int[] widths = {180, 180, 105, 120};
    for (int i = 0; i < headers.length; i++) {
      TableColumn column = new TableColumn(wMappings, SWT.LEFT);
      column.setText(headers[i]);
      column.setWidth(widths[i]);
    }
    GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
    tableData.heightHint = 120;
    wMappings.setLayoutData(tableData);
    PropsUi.setLook(wMappings);
    wMappings.addListener(SWT.Selection, event -> loadSelectedMapping());

    Composite buttons = new Composite(content, SWT.NONE);
    GridLayout buttonsLayout = new GridLayout(4, false);
    buttonsLayout.marginHeight = 0;
    buttonsLayout.marginWidth = 0;
    buttons.setLayout(buttonsLayout);
    buttons.setLayoutData(span(2));
    PropsUi.setLook(buttons);
    Button add = new Button(buttons, SWT.PUSH);
    add.setText("Add");
    add.addListener(SWT.Selection, event -> {
      addMappingRow(new JsonMapping());
      wMappings.setSelection(wMappings.getItemCount() - 1);
      wMappings.showSelection();
      loadSelectedMapping();
      wMappingKey.setFocus();
      markChanged();
    });
    wRemoveMapping = new Button(buttons, SWT.PUSH);
    wRemoveMapping.setText("Remove");
    wRemoveMapping.addListener(SWT.Selection, event -> {
      int[] indices = wMappings.getSelectionIndices();
      if (indices.length == 0) {
        return;
      }
      wMappings.remove(indices);
      wMappings.setSelection(Math.min(indices[0], wMappings.getItemCount() - 1));
      loadSelectedMapping();
      markChanged();
    });
    wMoveMappingUp = new Button(buttons, SWT.PUSH);
    wMoveMappingUp.setText("Up");
    wMoveMappingUp.addListener(SWT.Selection, event -> moveMapping(-1));
    wMoveMappingDown = new Button(buttons, SWT.PUSH);
    wMoveMappingDown.setText("Down");
    wMoveMappingDown.addListener(SWT.Selection, event -> moveMapping(1));

    addLabel(content, "Key");
    wMappingKey = ValueOrFieldControl.builder(content, variables)
        .editor(EditorKind.TEXT)
        .fieldProvider(() -> fieldNames)
        .onChange(() -> {
          TableItem item = selectedMappingRow();
          if (item != null) {
            JsonMapping mapping = (JsonMapping) item.getData();
            ValueOrField key = wMappingKey.getValue();
            mapping.setKeySource(key.mode());
            mapping.setKey(key.configuredValue());
            mapping.setKeyField(key.fieldName());
            mappingChanged(item);
          }
        })
        .build();
    wMappingKey.setLayoutData(fill());

    addLabel(content, "Value");
    wMappingValue = ValueOrFieldControl.builder(content, variables)
        .editor(EditorKind.TEXT)
        .fieldProvider(() -> fieldNames)
        .onChange(() -> {
          TableItem item = selectedMappingRow();
          if (item != null) {
            JsonMapping mapping = (JsonMapping) item.getData();
            ValueOrField value = wMappingValue.getValue();
            mapping.setValueSource(value.mode());
            mapping.setValue(value.configuredValue());
            mapping.setValueField(value.fieldName());
            mappingChanged(item);
          }
        })
        .build();
    wMappingValue.setLayoutData(fill());

    addLabel(content, "Value type");
    Composite options = new Composite(content, SWT.NONE);
    GridLayout optionsLayout = new GridLayout(2, false);
    optionsLayout.marginWidth = 0;
    optionsLayout.marginHeight = 0;
    options.setLayout(optionsLayout);
    options.setLayoutData(fill());
    PropsUi.setLook(options);
    wMappingType = new Combo(options, SWT.DROP_DOWN | SWT.READ_ONLY);
    wMappingType.setItems(JsonValueType.descriptions());
    wMappingType.addListener(SWT.Selection, event -> {
      TableItem item = selectedMappingRow();
      if (item != null) {
        ((JsonMapping) item.getData()).setValueType(
            JsonValueType.lookupDescription(wMappingType.getText(), JsonValueType.AUTO));
        mappingChanged(item);
      }
    });
    wSkipWhenNull = new Button(options, SWT.CHECK);
    wSkipWhenNull.setText("Skip when null");
    wSkipWhenNull.addListener(SWT.Selection, event -> {
      TableItem item = selectedMappingRow();
      if (item != null) {
        ((JsonMapping) item.getData()).setSkipIfNull(wSkipWhenNull.getSelection());
        mappingChanged(item);
      }
    });
  }

  private void addMappingRow(JsonMapping mapping) {
    TableItem item = new TableItem(wMappings, SWT.NONE);
    item.setData(mapping);
    showMapping(item);
  }

  private void showMapping(TableItem item) {
    JsonMapping mapping = (JsonMapping) item.getData();
    item.setText(new String[] {
      mapping.getKeySource() == SourceMode.FIELD
          ? "Field: " + nvl(mapping.getKeyField()) : nvl(mapping.getKey()),
      mapping.getValueSource() == SourceMode.FIELD
          ? "Field: " + nvl(mapping.getValueField()) : nvl(mapping.getValue()),
      mapping.getValueType().getDescription(),
      mapping.isSkipIfNull() ? "Y" : "N"
    });
  }

  private void mappingChanged(TableItem item) {
    showMapping(item);
    markChanged();
  }

  private TableItem selectedMappingRow() {
    return wMappings.getSelectionCount() == 1
        ? wMappings.getItem(wMappings.getSelectionIndex())
        : null;
  }

  private void loadSelectedMapping() {
    TableItem item = selectedMappingRow();
    JsonMapping mapping = item == null ? new JsonMapping() : (JsonMapping) item.getData();
    wMappingKey.setValue(
        new ValueOrField(mapping.getKeySource(), mapping.getKey(), mapping.getKeyField()));
    wMappingValue.setValue(
        new ValueOrField(mapping.getValueSource(), mapping.getValue(), mapping.getValueField()));
    wMappingType.setText(mapping.getValueType().getDescription());
    wSkipWhenNull.setSelection(mapping.isSkipIfNull());

    boolean selected = item != null;
    wMappingKey.setEnabled(selected);
    wMappingValue.setEnabled(selected);
    wMappingType.setEnabled(selected);
    wSkipWhenNull.setEnabled(selected);
    wRemoveMapping.setEnabled(wMappings.getSelectionCount() > 0);
    wMoveMappingUp.setEnabled(selected && wMappings.getSelectionIndex() > 0);
    wMoveMappingDown.setEnabled(
        selected && wMappings.getSelectionIndex() < wMappings.getItemCount() - 1);
  }

  private void moveMapping(int delta) {
    TableItem current = selectedMappingRow();
    int destination = wMappings.getSelectionIndex() + delta;
    if (current == null || destination < 0 || destination >= wMappings.getItemCount()) {
      return;
    }
    TableItem adjacent = wMappings.getItem(destination);
    Object mapping = current.getData();
    current.setData(adjacent.getData());
    adjacent.setData(mapping);
    showMapping(current);
    showMapping(adjacent);
    wMappings.setSelection(destination);
    wMappings.showSelection();
    loadSelectedMapping();
    markChanged();
  }

  private void loadFieldNames() {
    IRowMeta rowMeta = previousFields();
    if (rowMeta == null) {
      return;
    }
    List<String> names = new ArrayList<>();
    for (int i = 0; i < rowMeta.size(); i++) {
      names.add(rowMeta.getValueMeta(i).getName());
    }
    fieldNames = names.toArray(new String[0]);
  }

  private IRowMeta previousFields() {
    TransformMeta transformMeta = originalTransform;
    if (transformMeta == null) {
      return null;
    }
    try {
      return pipelineMeta.getPrevTransformFields(variables, transformMeta);
    } catch (HopException e) {
      logError("Unable to determine the input fields: " + e.getMessage());
      return null;
    }
  }

  private void getData() {
    wOutputField.setText(input.getOutputField() == null ? "" : input.getOutputField());
    wOutputType.setText(input.getOutputType().getDescription());
    wPrettyPrint.setSelection(input.isPrettyPrint());
    wModeCreate.setSelection(input.getMode() == JsonBuilderMode.CREATE);
    wModeInsert.setSelection(input.getMode() == JsonBuilderMode.INSERT);
    wBaseJsonField.setText(input.getBaseJsonField() == null ? "" : input.getBaseJsonField());
    wJsonPointer.setText(input.getJsonPointer() == null ? "" : input.getJsonPointer());

    for (JsonMapping mapping : input.getMappings()) {
      addMappingRow(mapping.clone());
    }
    wMappings.setSelection(0);
    loadSelectedMapping();

    for (int i = 0; i < input.getGroupByFields().size(); i++) {
      TableItem item = wGroupBy.table.getItem(i);
      item.setText(1, nvl(input.getGroupByFields().get(i)));
    }
    wGroupBy.setRowNums();
    wGroupBy.optWidth(true);

    updateModeEnablement();
  }

  private void updateModeEnablement() {
    boolean insert = wModeInsert.getSelection();
    wlBaseJsonField.setEnabled(insert);
    wBaseJsonField.setEnabled(insert);
    wlJsonPointer.setEnabled(insert);
    wJsonPointer.setEnabled(insert);
    markChanged();
  }

  private void markChanged() {
    if (!loading) {
      input.setChanged();
    }
  }

  private void ok() {
    if (Utils.isEmpty(wTransformName.getText())) {
      return;
    }
    JsonObjectBuilderMeta candidate = new JsonObjectBuilderMeta(input);

    candidate.setOutputField(wOutputField.getText());
    candidate.setOutputType(
        JsonOutputType.lookupDescription(wOutputType.getText(), JsonOutputType.JSON));
    candidate.setPrettyPrint(wPrettyPrint.getSelection());
    candidate.setMode(wModeInsert.getSelection() ? JsonBuilderMode.INSERT : JsonBuilderMode.CREATE);
    candidate.setBaseJsonField(wBaseJsonField.getText());
    candidate.setJsonPointer(wJsonPointer.getText());

    candidate.getMappings().clear();
    for (TableItem item : wMappings.getItems()) {
      JsonMapping mapping = (JsonMapping) item.getData();
      if (Utils.isEmpty(mapping.getKey())
          && Utils.isEmpty(mapping.getKeyField())
          && Utils.isEmpty(mapping.getValue())
          && Utils.isEmpty(mapping.getValueField())) {
        continue;
      }
      candidate.getMappings().add(mapping);
    }

    candidate.getGroupByFields().clear();
    for (TableItem item : wGroupBy.getNonEmptyItems()) {
      String field = item.getText(1);
      if (!Utils.isEmpty(field)) {
        candidate.getGroupByFields().add(field);
      }
    }

    try {
      candidate.validate(previousFields(), variables);
    } catch (Exception e) {
      showWarning(e.getMessage());
      return;
    }
    input.copyConfigurationFrom(candidate);
    input.setChanged();
    transformName = wTransformName.getText();
    dispose();
  }

  void showWarning(String message) {
    MessageBox messageBox = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK);
    messageBox.setText("Invalid configuration");
    messageBox.setMessage(message);
    messageBox.open();
  }

  private void cancel() {
    transformName = null;
    input.setChanged(changed);
    dispose();
  }

  private Label addLabel(Composite parent, String text) {
    Label label = new Label(parent, SWT.RIGHT);
    label.setText(text);
    label.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
    PropsUi.setLook(label);
    return label;
  }

  private GridData fill() {
    return new GridData(SWT.FILL, SWT.CENTER, true, false);
  }

  private GridData span(int columns) {
    return new GridData(SWT.FILL, SWT.CENTER, true, false, columns, 1);
  }

  private String nvl(String text) {
    return text == null ? "" : text;
  }
}
