package dk.dtu.collumns;

import dk.dtu.methods.Helpers;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class TaskReorderColumn implements Column<Helpers.TaskEntry> {

    private ImageView createIcon() {
        try {
            ImageView icon = new ImageView(new Image(getClass().getResourceAsStream("/Icons/reordericon.png")));
            icon.setFitWidth(28);
            icon.setFitHeight(28);
            icon.setPreserveRatio(true);
            return icon;
        } catch (Exception e) {
            return new ImageView();
        }
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
    public Node createHeader(ColumnHeaderContext<Helpers.TaskEntry> ctx) {
        Label label = new Label("");
        label.setPrefWidth(prefWidth());
        label.setMinWidth(prefWidth());
        label.setMaxWidth(prefWidth());
        return label;
    }

    @Override
    public ColumnCell<Helpers.TaskEntry> createCell(ColumnCellContext<Helpers.TaskEntry> ctx) {
        final ImageView icon = createIcon();

        icon.getStyleClass().add("reorder-handle");

        return new ColumnCell<>() {
            @Override
            public Node node() {
                icon.setFitWidth(28);
                icon.setFitHeight(28);
                return icon;
            }

            @Override
            public void update(Helpers.TaskEntry item) {
                // no-op (the scene wires up drag/drop events on this node)
            }
        };
    }
}
