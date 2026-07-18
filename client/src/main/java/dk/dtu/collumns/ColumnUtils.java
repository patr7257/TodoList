package dk.dtu.collumns;

import javafx.geometry.Pos;
import javafx.scene.control.Label;

import java.util.List;

public final class ColumnUtils {
    private ColumnUtils() {}

    public static Label createSortableHeaderLabel(String title, double prefWidth, Runnable onClick) {
        Label label = new Label(title + " ▲▼");
        label.setPrefWidth(prefWidth);
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setAlignment(Pos.CENTER);
        label.getStyleClass().add("sort-header");
        if (onClick != null) {
            label.setOnMouseClicked(e -> onClick.run());
        }
        return label;
    }

    public static Label createHeaderLabel(String title, double prefWidth) {
        Label label = new Label(title);
        label.setPrefWidth(prefWidth);
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setAlignment(Pos.CENTER);
        label.getStyleClass().add("sort-header");
        return label;
    }

    public static void setActiveHeader(Label active, List<Label> allHeaders) {
        for (Label header : allHeaders) {
            header.getStyleClass().removeAll("sort-header", "sort-header-active");
            header.getStyleClass().add(header == active ? "sort-header-active" : "sort-header");
        }
    }
}
