package dk.dtu.collumns;

import dk.dtu.methods.Helpers;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.geometry.Pos;
import javafx.scene.text.TextAlignment;

import java.util.Comparator;

public class ListNameColumn implements Column<Helpers.ListEntry> {

    @Override
    public String id() {
        return "name";
    }

    @Override
    public String title() {
        return "List Name";
    }

    @Override
    public double prefWidth() {
        return 300;
    }

    @Override
    public Comparator<Helpers.ListEntry> comparator() {
        return Comparator.comparing(e -> e.name != null ? e.name : "", String.CASE_INSENSITIVE_ORDER);
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
        label.setTextAlignment(TextAlignment.CENTER);
        label.getStyleClass().add("list-col-name");

        return new ColumnCell<>() {
            @Override
            public Node node() {
                return label;
            }

            @Override
            public void update(Helpers.ListEntry item) {
                if (item == null) {
                    label.setText("");
                    label.setTooltip(null);
                    return;
                }
                label.setText(item.name);
                label.setTooltip(new Tooltip(item.name));
            }
        };
    }
}
