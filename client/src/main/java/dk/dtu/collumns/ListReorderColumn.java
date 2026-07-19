package dk.dtu.collumns;

import dk.dtu.methods.Helpers;
import dk.dtu.ui.Icons;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public class ListReorderColumn implements Column<Helpers.ListEntry> {

    private StackPane createHandle() {
        StackPane handle = new StackPane(Icons.reorder());
        handle.setAlignment(Pos.CENTER);
        handle.getStyleClass().add("reorder-handle");
        return handle;
    }

    @Override
    public String id() {
        return "reorder";
    }

    @Override
    public String title() {
        return "";
    }

    @Override
    public double prefWidth() {
        return 50;
    }

    @Override
    public Node createHeader(ColumnHeaderContext<Helpers.ListEntry> ctx) {
        Label label = new Label("");
        label.setPrefWidth(prefWidth());
        label.setMinWidth(prefWidth());
        label.setMaxWidth(prefWidth());
        label.setAlignment(javafx.geometry.Pos.CENTER);
        return label;
    }

    @Override
    public ColumnCell<Helpers.ListEntry> createCell(ColumnCellContext<Helpers.ListEntry> ctx) {
        final StackPane handle = createHandle();

        return new ColumnCell<>() {
            @Override
            public Node node() {
                return handle;
            }

            @Override
            public void update(Helpers.ListEntry item) {
                // no-op (the scene wires up drag/drop events on this node)
            }
        };
    }
}
