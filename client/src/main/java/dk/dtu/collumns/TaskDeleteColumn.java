package dk.dtu.collumns;

import dk.dtu.methods.Helpers;
import dk.dtu.methods.Tasks;
import dk.dtu.shared.Config;
import dk.dtu.ui.Icons;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import org.kordamp.ikonli.javafx.FontIcon;

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
        Label label = ColumnUtils.createHeaderLabel("", prefWidth());
        return label;
    }

    @Override
    public ColumnCell<Helpers.TaskEntry> createCell(ColumnCellContext<Helpers.TaskEntry> ctx) {
        Button deleteButton = new Button();
        deleteButton.setPrefWidth(prefWidth());
        deleteButton.setMinWidth(prefWidth());
        deleteButton.setMaxWidth(prefWidth());
        deleteButton.getStyleClass().add("task-col-delete-button");

        FontIcon deleteIcon = Icons.delete();
        deleteIcon.getStyleClass().add("icon-delete");
        deleteButton.setGraphic(deleteIcon);

        deleteButton.addEventFilter(MouseEvent.MOUSE_PRESSED, evt -> {
            evt.consume();

            Helpers.TaskEntry item = ctx.currentItem().get();
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
