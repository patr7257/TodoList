package dk.dtu.collumns;

import dk.dtu.methods.Helpers;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.geometry.Pos;
import javafx.scene.text.TextAlignment;

import java.util.Comparator;

public class TaskTitleColumn implements Column<Helpers.TaskEntry> {

    @Override
    public String id() {
        return "title";
    }

    @Override
    public String title() {
        return "Name";
    }

    @Override
    public double prefWidth() {
        return 250;
    }

    @Override
    public Comparator<Helpers.TaskEntry> comparator() {
        return Comparator.comparing(e -> e.title != null ? e.title : "", String.CASE_INSENSITIVE_ORDER);
    }

    @Override
    public Node createHeader(ColumnHeaderContext<Helpers.TaskEntry> ctx) {
        return ColumnUtils.createSortableHeaderLabel(title(), prefWidth(), () -> ctx.onSortRequested().accept(this));
    }

    @Override
    public ColumnCell<Helpers.TaskEntry> createCell(ColumnCellContext<Helpers.TaskEntry> ctx) {
        Label label = new Label();
        label.setMinWidth(prefWidth());
        label.setPrefWidth(prefWidth());
        label.setMaxWidth(Double.MAX_VALUE);
        label.setAlignment(Pos.CENTER);
        label.setTextAlignment(TextAlignment.CENTER);
        label.getStyleClass().add("task-col-name");

        return new ColumnCell<>() {
            @Override
            public Node node() {
                return label;
            }

            @Override
            public void update(Helpers.TaskEntry item) {
                if (item == null) {
                    label.setText("");
                    label.setTooltip(null);
                    return;
                }
                String text = item.nameToString();
                label.setText(text);
                label.setTooltip(new Tooltip(text));
            }
        };
    }
}
