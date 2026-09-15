package ch.so.agi.hop.json.builder.transform;

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
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

public class JsonObjectBuilderDialog extends BaseTransformDialog {

  private static final String COMBO_NO = "N";
  private static final String COMBO_YES = "Y";

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
  private TableView wMappings;
  private TableView wGroupBy;
  private ColumnInfo[] mappingColumns;
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

    Label wlMappings = new Label(content, SWT.NONE);
    wlMappings.setText("Field mappings (key/value pairs, dynamic keys are read from fields):");
    wlMappings.setLayoutData(span(2));
    PropsUi.setLook(wlMappings);

    mappingColumns =
        new ColumnInfo[] {
          new ColumnInfo("Key / key field", ColumnInfo.COLUMN_TYPE_CCOMBO, fieldNames, false),
          new ColumnInfo("Key source", ColumnInfo.COLUMN_TYPE_CCOMBO, JsonKeySource.descriptions()),
          new ColumnInfo("Value / value field", ColumnInfo.COLUMN_TYPE_CCOMBO, fieldNames, false),
          new ColumnInfo(
              "Value source", ColumnInfo.COLUMN_TYPE_CCOMBO, JsonValueSource.descriptions()),
          new ColumnInfo("Type", ColumnInfo.COLUMN_TYPE_CCOMBO, JsonValueType.descriptions()),
          new ColumnInfo(
              "Skip when null", ColumnInfo.COLUMN_TYPE_CCOMBO, new String[] {COMBO_NO, COMBO_YES})
        };
    wMappings =
        new TableView(
            variables,
            content,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI,
            mappingColumns,
            Math.max(1, input.getMappings().size()),
            lsMod,
            props);
    GridData mappingsData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
    mappingsData.heightHint = 140;
    wMappings.setLayoutData(mappingsData);

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

    for (int i = 0; i < input.getMappings().size(); i++) {
      JsonMapping mapping = input.getMappings().get(i);
      TableItem item = wMappings.table.getItem(i);
      int column = 1;
      item.setText(
          column++,
          mapping.getKeySource() == JsonKeySource.LITERAL
              ? nvl(mapping.getKey())
              : nvl(mapping.getKeyField()));
      item.setText(column++, mapping.getKeySource().getDescription());
      item.setText(
          column++,
          mapping.getValueSource() == JsonValueSource.LITERAL
              ? nvl(mapping.getValue())
              : nvl(mapping.getValueField()));
      item.setText(column++, mapping.getValueSource().getDescription());
      item.setText(column++, mapping.getValueType().getDescription());
      item.setText(column, mapping.isSkipIfNull() ? COMBO_YES : COMBO_NO);
    }
    wMappings.setRowNums();
    wMappings.optWidth(true);

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
    for (TableItem item : wMappings.getNonEmptyItems()) {
      String keyText = item.getText(1);
      String valueText = item.getText(3);
      if (Utils.isEmpty(keyText) && Utils.isEmpty(valueText)) {
        continue;
      }
      JsonMapping mapping = new JsonMapping();
      mapping.setKeySource(JsonKeySource.lookupDescription(item.getText(2), JsonKeySource.LITERAL));
      if (mapping.getKeySource() == JsonKeySource.LITERAL) {
        mapping.setKey(keyText);
      } else {
        mapping.setKeyField(keyText);
      }
      mapping.setValueSource(
          JsonValueSource.lookupDescription(item.getText(4), JsonValueSource.FIELD));
      if (mapping.getValueSource() == JsonValueSource.LITERAL) {
        mapping.setValue(valueText);
      } else {
        mapping.setValueField(valueText);
      }
      mapping.setValueType(JsonValueType.lookupDescription(item.getText(5), JsonValueType.AUTO));
      mapping.setSkipIfNull(COMBO_YES.equalsIgnoreCase(item.getText(6)));
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
