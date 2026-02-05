package dk.dtu.collumns;

import dk.dtu.methods.Helpers;
import dk.dtu.methods.Tasks;
import dk.dtu.shared.Config;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;

public class TaskDeleteColumn implements Column<Helpers.TaskEntry> {

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
    public Node createHeader(ColumnHeaderContext<Helpers.TaskEntry> ctx) {
        return ColumnUtils.createHeaderLabel("", prefWidth());
    }

    @Override
    public ColumnCell<Helpers.TaskEntry> createCell(ColumnCellContext<Helpers.TaskEntry> ctx) {
        javafx.scene.control.ListCell<Helpers.TaskEntry> cell = ctx.cell();

        Button deleteButton = new Button();
        deleteButton.setPrefWidth(prefWidth());
        deleteButton.setMinWidth(prefWidth());
        deleteButton.setMaxWidth(prefWidth());
        deleteButton.getStyleClass().add("list-col-delete-button");

        try {
            ImageView deleteIcon = new ImageView(new Image(getClass().getResourceAsStream("/Icons/deleteicon.png")));
            deleteIcon.setFitWidth(28);
            deleteIcon.setFitHeight(28);
            deleteButton.setGraphic(deleteIcon);
        } catch (Exception e) {
            deleteButton.setText("X");
        }

        deleteButton.addEventFilter(MouseEvent.MOUSE_PRESSED, evt -> {
            evt.consume();

            Helpers.TaskEntry item = cell.getItem();
            if (item == null) return;

            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Delete Task");
            confirmAlert.setHeaderText("Are you sure you want to delete this task?");
            confirmAlert.setContentText("Task: " + item.title + "\nThis action cannot be undone.");

            confirmAlert.showAndWait().ifPresent(response -> {
                if (response != ButtonType.OK) return;

                deleteButton.setDisable(true);
                new Thread(() -> {
                    try {
                        Tasks.deleteTask(Config.getRequestsUri(), Config.getResponsesUri(), item.id);
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
                }, "delete-task").start();
            });
        });

        return new ColumnCell<>() {
            @Override
            public Node node() {
                return deleteButton;
            }

            @Override
            public void update(Helpers.TaskEntry item) {
                // no-op
            }
        };
    }
}
