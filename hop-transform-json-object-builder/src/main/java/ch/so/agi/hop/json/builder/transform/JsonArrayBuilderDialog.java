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

public class JsonArrayBuilderDialog extends BaseTransformDialog {

  private final JsonArrayBuilderMeta input;

  private Text wOutputField;
  private Combo wOutputType;
  private Button wPrettyPrint;
  private Button wElementFieldRadio;
  private Button wElementRowRadio;
  private Combo wElementField;
  private Combo wElementType;
  private Label wlElementField;
  private Label wlElementType;
  private Button wInsertIntoTarget;
  private Combo wBaseJsonField;
  private Text wJsonPointer;
  private Label wlBaseJsonField;
  private Label wlJsonPointer;
  private Button wSkipNullElements;
  private TableView wGroupBy;
  private String[] fieldNames = new String[0];

  public JsonArrayBuilderDialog(
      Shell parent, IVariables variables, JsonArrayBuilderMeta transformMeta, PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    this.input = transformMeta;
  }

  @Override
  public String open() {
    createShell("JSON Array Builder");

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

    addLabel(content, "Element");
    Composite elementComposite = new Composite(content, SWT.NONE);
    elementComposite.setLayoutData(fill());
    GridLayout elementLayout = new GridLayout(1, false);
    elementLayout.marginWidth = 0;
    elementLayout.marginHeight = 0;
    elementComposite.setLayout(elementLayout);
    wElementFieldRadio = new Button(elementComposite, SWT.RADIO);
    wElementFieldRadio.setText(JsonElementMode.FIELD_VALUE.getDescription());
    wElementRowRadio = new Button(elementComposite, SWT.RADIO);
    wElementRowRadio.setText(JsonElementMode.WHOLE_ROW.getDescription());
    wElementFieldRadio.addListener(SWT.Selection, e -> updateElementEnablement());
    wElementRowRadio.addListener(SWT.Selection, e -> updateElementEnablement());

    wlElementField = addLabel(content, "Element field");
    wElementField = new Combo(content, SWT.DROP_DOWN | SWT.BORDER);
    wElementField.setItems(fieldNames);
    wElementField.setLayoutData(fill());
    wElementField.addModifyListener(lsMod);

    wlElementType = addLabel(content, "Element type");
    wElementType = new Combo(content, SWT.DROP_DOWN | SWT.READ_ONLY | SWT.BORDER);
    wElementType.setItems(JsonValueType.descriptions());
    wElementType.setLayoutData(fill());
    wElementType.addModifyListener(lsMod);

    addLabel(content, "Target");
    wInsertIntoTarget = new Button(content, SWT.CHECK);
    wInsertIntoTarget.setText("Insert into existing JSON object");
    PropsUi.setLook(wInsertIntoTarget);
    wInsertIntoTarget.addListener(SWT.Selection, e -> updateInsertEnablement());

    wlBaseJsonField = addLabel(content, "Base JSON field");
    wBaseJsonField = new Combo(content, SWT.DROP_DOWN | SWT.BORDER);
    wBaseJsonField.setItems(fieldNames);
    wBaseJsonField.setLayoutData(fill());
    wBaseJsonField.addModifyListener(lsMod);

    wlJsonPointer = addLabel(content, "JSON Pointer");
    wJsonPointer = new Text(content, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    wJsonPointer.setLayoutData(fill());
    wJsonPointer.addModifyListener(lsMod);

    addLabel(content, "");
    wSkipNullElements = new Button(content, SWT.CHECK);
    wSkipNullElements.setText("Skip null elements");
    PropsUi.setLook(wSkipNullElements);
    wSkipNullElements.addListener(SWT.Selection, e -> markChanged());

    Label wlGroupBy = new Label(content, SWT.NONE);
    wlGroupBy.setText("Group by fields (optional, one JSON array per group):");
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
    groupData.heightHint = 110;
    wGroupBy.setLayoutData(groupData);

    Label wStatus = new Label(content, SWT.WRAP);
    wStatus.setText(
        "Without group by fields all input rows are collected into a single array. "
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
    TransformMeta transformMeta = pipelineMeta.findTransform(transformName);
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
    wElementFieldRadio.setSelection(input.getElementMode() == JsonElementMode.FIELD_VALUE);
    wElementRowRadio.setSelection(input.getElementMode() == JsonElementMode.WHOLE_ROW);
    wElementField.setText(input.getElementField() == null ? "" : input.getElementField());
    wElementType.setText(input.getElementType().getDescription());
    wInsertIntoTarget.setSelection(input.isInsertIntoTarget());
    wBaseJsonField.setText(input.getBaseJsonField() == null ? "" : input.getBaseJsonField());
    wJsonPointer.setText(input.getJsonPointer() == null ? "" : input.getJsonPointer());
    wSkipNullElements.setSelection(input.isSkipNullElements());

    for (int i = 0; i < input.getGroupByFields().size(); i++) {
      TableItem item = wGroupBy.table.getItem(i);
      item.setText(1, input.getGroupByFields().get(i) == null ? "" : input.getGroupByFields().get(i));
    }
    wGroupBy.setRowNums();
    wGroupBy.optWidth(true);

    updateElementEnablement();
    updateInsertEnablement();
  }

  private void updateElementEnablement() {
    boolean fieldMode = wElementFieldRadio.getSelection();
    wlElementField.setEnabled(fieldMode);
    wElementField.setEnabled(fieldMode);
    wlElementType.setEnabled(fieldMode);
    wElementType.setEnabled(fieldMode);
    markChanged();
  }

  private void updateInsertEnablement() {
    boolean insert = wInsertIntoTarget.getSelection();
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
    transformName = wTransformName.getText();

    input.setOutputField(wOutputField.getText());
    input.setOutputType(JsonOutputType.lookupDescription(wOutputType.getText(), JsonOutputType.JSON));
    input.setPrettyPrint(wPrettyPrint.getSelection());
    input.setElementMode(
        wElementRowRadio.getSelection() ? JsonElementMode.WHOLE_ROW : JsonElementMode.FIELD_VALUE);
    input.setElementField(wElementField.getText());
    input.setElementType(JsonValueType.lookupDescription(wElementType.getText(), JsonValueType.AUTO));
    input.setInsertIntoTarget(wInsertIntoTarget.getSelection());
    input.setBaseJsonField(wBaseJsonField.getText());
    input.setJsonPointer(wJsonPointer.getText());
    input.setSkipNullElements(wSkipNullElements.getSelection());

    input.getGroupByFields().clear();
    for (TableItem item : wGroupBy.getNonEmptyItems()) {
      String field = item.getText(1);
      if (!Utils.isEmpty(field)) {
        input.getGroupByFields().add(field);
      }
    }

    try {
      input.validate(previousFields(), variables);
    } catch (Exception e) {
      showWarning(e.getMessage());
      return;
    }
    dispose();
  }

  private void showWarning(String message) {
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
}
