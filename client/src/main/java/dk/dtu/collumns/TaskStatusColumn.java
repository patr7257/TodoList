package dk.dtu.collumns;

import dk.dtu.methods.Helpers;
import dk.dtu.methods.Tasks;
import dk.dtu.shared.Config;
import dk.dtu.shared.TaskStatus;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;

import java.util.Comparator;

public class TaskStatusColumn implements Column<Helpers.TaskEntry> {

    private static final double CELL_HEIGHT = 35;

    @Override
    public String id() {
        return "status";
    }

    @Override
    public String title() {
        return "Status";
    }

    @Override
    public double prefWidth() {
        return 180;
    }

    @Override
    public Comparator<Helpers.TaskEntry> comparator() {
        return Comparator.comparing(e -> e.status != null ? e.status : "", String.CASE_INSENSITIVE_ORDER);
    }

    @Override
    public Node createHeader(ColumnHeaderContext<Helpers.TaskEntry> ctx) {
        return ColumnUtils.createSortableHeaderLabel(title(), prefWidth(), () -> ctx.onSortRequested().accept(this));
    }

    @Override
    public ColumnCell<Helpers.TaskEntry> createCell(ColumnCellContext<Helpers.TaskEntry> ctx) {
        ComboBox<TaskStatus> statusCombo = new ComboBox<>();
        statusCombo.setPrefWidth(prefWidth() - 10);
        statusCombo.setMinWidth(prefWidth() - 10);
        statusCombo.setMaxWidth(prefWidth() - 10);
        statusCombo.getItems().addAll(TaskStatus.values());
        statusCombo.setPromptText("Status");
        statusCombo.getStyleClass().addAll("task-col-status", "status-combo");

        statusCombo.setCellFactory(lv -> createStatusCell());
        statusCombo.setButtonCell(createStatusCell());

        statusCombo.setOnAction(evt -> {
            Helpers.TaskEntry item = ctx.currentItem().get();
            if (item == null) return;

            TaskStatus newStatus = statusCombo.getValue();
            if (newStatus == null) return;

            if (newStatus.name().equals(item.status)) return;

            statusCombo.setDisable(true);
            new Thread(() -> {
                try {
                    Tasks.changeTaskStatus(
                            Config.getRequestsUri(),
                            Config.getResponsesUri(),
                            item.listId,
                            item.id,
                            newStatus.name()
                    );
                    Platform.runLater(() -> {
                        statusCombo.setDisable(false);
                        // Refresh removed to prevent row shuffling during editing
                        // if (ctx.refresh() != null) {
                        //     ctx.refresh().run();
                        // }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> statusCombo.setDisable(false));
                }
            }, "change-task-status").start();
        });

        return new ColumnCell<>() {
            @Override
            public Node node() {
                return statusCombo;
            }

            @Override
            public void update(Helpers.TaskEntry item) {
                if (item == null) {
                    statusCombo.setValue(null);
                    return;
                }

                try {
                    statusCombo.setValue(TaskStatus.valueOf(item.status));
                } catch (Exception e) {
                    statusCombo.setValue(null);
                }
            }
        };
    }

    private ListCell<TaskStatus> createStatusCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(TaskStatus status, boolean empty) {
                super.updateItem(status, empty);
                getStyleClass().removeAll("status-NOT_STARTED", "status-IN_PROGRESS", "status-DELAYED", "status-NEED_HELP", "status-DONE");
                setAlignment(Pos.CENTER);
                setMinHeight(CELL_HEIGHT);
                setPrefHeight(CELL_HEIGHT);
                setMaxHeight(CELL_HEIGHT);
                setStyle("-fx-padding: 0 8 0 8;");
                if (empty || status == null) {
                    setText(null);
                } else {
                    setText(status.name());
                    getStyleClass().add("status-" + status.name());
                }
            }
        };
    }
}
