package dk.dtu.collumns;

import dk.dtu.methods.Helpers;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.Comparator;

public class ListOverdueColumn implements Column<Helpers.ListEntry> {

    @Override
    public String id() {
        return "overdue";
    }

    @Override
    public String title() {
        return "Overdue";
    }

    @Override
    public double prefWidth() {
        return 110;
    }

    @Override
    public Comparator<Helpers.ListEntry> comparator() {
        return Comparator.comparingInt(e -> e.overdueTaskCount);
    }

    @Override
    public Node createHeader(ColumnHeaderContext<Helpers.ListEntry> ctx) {
        return ColumnUtils.createSortableHeaderLabel(title(), prefWidth(), () -> ctx.onSortRequested().accept(this));
    }

    @Override
    public ColumnCell<Helpers.ListEntry> createCell(ColumnCellContext<Helpers.ListEntry> ctx) {
        Circle circle = new Circle(8);
        StackPane pane = new StackPane(circle);
        pane.setPrefWidth(prefWidth());
        pane.setMinWidth(prefWidth());
        pane.setMaxWidth(prefWidth());
        pane.setAlignment(Pos.CENTER);

        return new ColumnCell<>() {
            @Override
            public Node node() {
                return pane;
            }

            @Override
            public void update(Helpers.ListEntry item) {
                if (item == null) {
                    circle.setFill(Color.TRANSPARENT);
                    Tooltip.uninstall(pane, null);
                    return;
                }

                if (item.overdueTaskCount > 0) {
                    circle.setFill(Color.rgb(220, 53, 69));
                    Tooltip.install(pane, new Tooltip("This list contains overdue tasks (" + item.overdueTaskCount + ")"));
                } else {
                    circle.setFill(Color.rgb(40, 167, 69));
                    Tooltip.install(pane, new Tooltip("This list has no overdue tasks"));
                }
            }
        };
    }
}
