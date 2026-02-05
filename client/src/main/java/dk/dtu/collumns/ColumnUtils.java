package dk.dtu.collumns;

import javafx.geometry.Pos;
import javafx.scene.control.Label;

import java.util.List;

public final class ColumnUtils {
    private ColumnUtils() {}

    public static Label createSortableHeaderLabel(String title, double prefWidth, Runnable onClick) {
        Label label = new Label(title + " ▲▼");
        label.setPrefWidth(prefWidth);
        label.setMinWidth(prefWidth);
        label.setMaxWidth(prefWidth);
        label.setAlignment(Pos.CENTER);
        label.setStyle("-fx-font-weight: bold; -fx-cursor: hand;");
        if (onClick != null) {
            label.setOnMouseClicked(e -> onClick.run());
        }
        return label;
    }

    public static Label createHeaderLabel(String title, double prefWidth) {
        Label label = new Label(title);
        label.setPrefWidth(prefWidth);
        label.setMinWidth(prefWidth);
        label.setMaxWidth(prefWidth);
        label.setAlignment(Pos.CENTER);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    public static void setActiveHeader(Label active, List<Label> allHeaders) {
        for (Label header : allHeaders) {
            if (header == active) {
                header.setStyle("-fx-font-weight: bold; -fx-cursor: hand; -fx-text-fill: #007bff;");
            } else {
                header.setStyle("-fx-font-weight: bold; -fx-cursor: hand;");
            }
        }
    }
}
