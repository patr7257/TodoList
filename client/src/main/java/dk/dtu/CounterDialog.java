package dk.dtu;

import dk.dtu.net.CounterDto;
import dk.dtu.ui.Icons;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

/**
 * Add/edit dialog for a single "fun counter". The icon picker is a whitelist
 * ({@link Icons#COUNTER_ICON_CHOICES}), since Ikonli Feather has no plane,
 * ship, or walking glyph and a free-text icon column is only meant to be
 * hand-edited by a developer, never typed by a user through this dialog.
 * Always goes through {@link DarkModeManager#prepareDialog(Dialog, Window)}.
 */
public class CounterDialog extends Dialog<CounterDialog.Result> {

    private static final String NO_ICON = "(none)";

    /** The validated user input; {@code icon} is null for "(none)". */
    public record Result(String label, String description, int value, String icon) {
    }

    private final TextField labelField = new TextField();
    private final TextArea descriptionArea = new TextArea();
    private final Spinner<Integer> valueSpinner = new Spinner<>(Integer.MIN_VALUE, Integer.MAX_VALUE, 0);
    private final ComboBox<String> iconCombo = new ComboBox<>();

    public CounterDialog(Window owner, CounterDto existing) {
        setTitle(existing == null ? "Add counter" : "Edit counter");
        setHeaderText(existing == null ? "Add a new fun counter" : "Edit counter");
        DarkModeManager.prepareDialog(this, owner);

        labelField.setPromptText("Label");
        labelField.setPrefWidth(260);

        descriptionArea.setPromptText("Description (optional)");
        descriptionArea.setPrefRowCount(3);
        descriptionArea.setWrapText(true);

        valueSpinner.setEditable(true);
        valueSpinner.setPrefWidth(120);

        iconCombo.getItems().add(NO_ICON);
        iconCombo.getItems().addAll(Icons.COUNTER_ICON_CHOICES);
        iconCombo.setValue(NO_ICON);
        iconCombo.setPrefWidth(180);

        if (existing != null) {
            labelField.setText(existing.label());
            descriptionArea.setText(existing.description() == null ? "" : existing.description());
            valueSpinner.getValueFactory().setValue(existing.value());
            if (existing.icon() != null && Icons.COUNTER_ICON_CHOICES.contains(existing.icon())) {
                iconCombo.setValue(existing.icon());
            }
        }

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label("Label"), labelField);
        grid.addRow(1, new Label("Description"), descriptionArea);
        grid.addRow(2, new Label("Value"), valueSpinner);
        grid.addRow(3, new Label("Icon"), iconCombo);

        getDialogPane().setContent(grid);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setPrefWidth(380);

        setResultConverter(btn -> {
            if (btn != ButtonType.OK) {
                return null;
            }
            String label = labelField.getText() == null ? "" : labelField.getText().trim();
            if (label.isEmpty()) {
                return null;
            }
            String description = descriptionArea.getText();
            String icon = NO_ICON.equals(iconCombo.getValue()) ? null : iconCombo.getValue();
            Integer value = valueSpinner.getValue();
            return new Result(label, description, value == null ? 0 : value, icon);
        });
    }
}
