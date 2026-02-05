package dk.dtu.collumns;

import dk.dtu.methods.Helpers;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;

import java.util.Comparator;

public class ListTasksCountColumn implements Column<Helpers.ListEntry> {

    @Override
    public String id() {
        return "tasks";
    }

    @Override
    public String title() {
        return "Tasks";
    }

    @Override
    public double prefWidth() {
        return 95;
    }

    @Override
    public Comparator<Helpers.ListEntry> comparator() {
        return Comparator.comparingInt(e -> e.taskCount);
    }

    @Override
    public Node createHeader(ColumnHeaderContext<Helpers.ListEntry> ctx) {
        return ColumnUtils.createSortableHeaderLabel(title(), prefWidth(), () -> ctx.onSortRequested().accept(this));
    }

    @Override
    public ColumnCell<Helpers.ListEntry> createCell(ColumnCellContext<Helpers.ListEntry> ctx) {
        Label label = new Label();
        label.setPrefWidth(prefWidth());
        label.setMinWidth(prefWidth());
        label.setMaxWidth(prefWidth());
        label.setAlignment(Pos.CENTER);
        label.getStyleClass().add("list-col-count");

        return new ColumnCell<>() {
            @Override
            public Node node() {
                return label;
            }

            @Override
            public void update(Helpers.ListEntry item) {
                label.setText(item == null ? "" : String.valueOf(item.taskCount));
            }
        };
    }
}
