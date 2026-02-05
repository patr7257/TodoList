package dk.dtu.collumns;

import dk.dtu.methods.Helpers;
import dk.dtu.methods.Tasks;
import dk.dtu.shared.Config;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ListCell;

import java.time.LocalDate;
import java.util.Comparator;

public class TaskDueDateColumn implements Column<Helpers.TaskEntry> {

    @Override
    public String id() {
        return "dueDate";
    }

    @Override
    public String title() {
        return "Due date";
    }

    @Override
    public double prefWidth() {
        return 145;
    }

    @Override
    public Comparator<Helpers.TaskEntry> comparator() {
        return Comparator.comparing(e -> (e.dueDate != null && !e.dueDate.isBlank()) ? e.dueDate : "9999-12-31");
    }

    @Override
    public Node createHeader(ColumnHeaderContext<Helpers.TaskEntry> ctx) {
        return ColumnUtils.createSortableHeaderLabel(title(), prefWidth(), () -> ctx.onSortRequested().accept(this));
    }

    @Override
    public ColumnCell<Helpers.TaskEntry> createCell(ColumnCellContext<Helpers.TaskEntry> ctx) {
        ListCell<Helpers.TaskEntry> cell = ctx.cell();

        DatePicker duePicker = new DatePicker();
        duePicker.setPrefWidth(prefWidth());
        duePicker.setMinWidth(prefWidth());
        duePicker.setMaxWidth(prefWidth());
        duePicker.setPromptText("Due date");
        duePicker.getStyleClass().add("task-col-due");
        try {
            duePicker.getEditor().setAlignment(Pos.CENTER);
        } catch (Exception ignored) {
        }

        duePicker.setOnAction(evt -> {
            Helpers.TaskEntry item = cell.getItem();
            if (item == null) return;

            LocalDate newDate = duePicker.getValue();
            if (newDate == null) return;

            String newDueDate = newDate.toString();
            if (newDueDate.equals(item.dueDate)) return;

            duePicker.setDisable(true);
            new Thread(() -> {
                try {
                    Tasks.changeTaskDueDate(
                            Config.getRequestsUri(),
                            Config.getResponsesUri(),
                            item.listId,
                            item.id,
                            newDueDate
                    );
                    Platform.runLater(() -> {
                        duePicker.setDisable(false);
                        if (ctx.refresh() != null) {
                            ctx.refresh().run();
                        }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> duePicker.setDisable(false));
                }
            }, "change-due-date").start();
        });

        return new ColumnCell<>() {
            @Override
            public Node node() {
                return duePicker;
            }

            @Override
            public void update(Helpers.TaskEntry item) {
                if (item == null) {
                    duePicker.setValue(null);
                    return;
                }

                try {
                    if (item.dueDate != null && !item.dueDate.isBlank()) {
                        duePicker.setValue(LocalDate.parse(item.dueDate));
                    } else {
                        duePicker.setValue(null);
                    }
                } catch (Exception e) {
                    duePicker.setValue(null);
                }
            }
        };
    }
}
