package dk.dtu.collumns;

import dk.dtu.methods.Helpers;
import dk.dtu.methods.Lists;
import dk.dtu.shared.Config;
import dk.dtu.ui.Icons;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import org.kordamp.ikonli.javafx.FontIcon;

public class ListDeleteColumn implements Column<Helpers.ListEntry> {

    @Override
    public String id() {
        return "delete";
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
        Label label = ColumnUtils.createHeaderLabel("", prefWidth());
        return label;
    }

    @Override
    public ColumnCell<Helpers.ListEntry> createCell(ColumnCellContext<Helpers.ListEntry> ctx) {
        Button deleteButton = new Button();
        deleteButton.setPrefWidth(prefWidth());
        deleteButton.setMinWidth(prefWidth());
        deleteButton.setMaxWidth(prefWidth());
        deleteButton.getStyleClass().add("list-col-delete-button");

        FontIcon deleteIcon = Icons.delete();
        deleteIcon.getStyleClass().add("icon-delete");
        deleteButton.setGraphic(deleteIcon);

        // Prevent bubbling to ListView clicks
        deleteButton.addEventFilter(MouseEvent.MOUSE_PRESSED, evt -> {
            evt.consume();

            Helpers.ListEntry item = ctx.currentItem().get();
            if (item == null) return;

            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Delete List");
            confirmAlert.setHeaderText("Are you sure you want to delete this list?");
            confirmAlert.setContentText("List: " + item.name + "\nThis action cannot be undone.");

            confirmAlert.showAndWait().ifPresent(response -> {
                if (response != ButtonType.OK) return;

                deleteButton.setDisable(true);
                new Thread(() -> {
                    try {
                        Lists.deleteTodoList(Config.getRequestsUri(), Config.getResponsesUri(), item.id);
                        Platform.runLater(() -> {
                            deleteButton.setDisable(false);
                            if (ctx.refresh() != null) {
                                ctx.refresh().run();
                            }
                        });
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        Platform.runLater(() -> deleteButton.setDisable(false));
                    }
                }, "delete-list").start();
            });
        });

        return new ColumnCell<>() {
            @Override
            public Node node() {
                return deleteButton;
            }

            @Override
            public void update(Helpers.ListEntry item) {
                // no-op
            }
        };
    }
}
