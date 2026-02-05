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
        return 145;
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
        ListCell<Helpers.TaskEntry> cell = ctx.cell();

        ComboBox<TaskStatus> statusCombo = new ComboBox<>();
        statusCombo.setPrefWidth(prefWidth());
        statusCombo.setMinWidth(prefWidth());
        statusCombo.setMaxWidth(prefWidth());
        statusCombo.getItems().addAll(TaskStatus.values());
        statusCombo.setPromptText("Status");
        statusCombo.getStyleClass().add("task-col-status");

        statusCombo.setCellFactory(lv -> createStatusCell());
        statusCombo.setButtonCell(createStatusCell());

        statusCombo.setOnAction(evt -> {
            Helpers.TaskEntry item = cell.getItem();
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
                        if (ctx.refresh() != null) {
                            ctx.refresh().run();
                        }
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
