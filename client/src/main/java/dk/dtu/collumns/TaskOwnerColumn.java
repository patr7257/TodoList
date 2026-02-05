package dk.dtu.collumns;

import dk.dtu.methods.Helpers;
import dk.dtu.methods.Tasks;
import dk.dtu.methods.Users;
import dk.dtu.shared.Config;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;

import java.util.Comparator;

public class TaskOwnerColumn implements Column<Helpers.TaskEntry> {

    private static final String ALL = "All";

    @Override
    public String id() {
        return "owner";
    }

    @Override
    public String title() {
        return "Owner";
    }

    @Override
    public double prefWidth() {
        return 145;
    }

    @Override
    public Comparator<Helpers.TaskEntry> comparator() {
        return Comparator.comparing(e -> e.owner != null ? e.owner : "", String.CASE_INSENSITIVE_ORDER);
    }

    @Override
    public Node createHeader(ColumnHeaderContext<Helpers.TaskEntry> ctx) {
        return ColumnUtils.createSortableHeaderLabel(title(), prefWidth(), () -> ctx.onSortRequested().accept(this));
    }

    @Override
    public ColumnCell<Helpers.TaskEntry> createCell(ColumnCellContext<Helpers.TaskEntry> ctx) {
        ListCell<Helpers.TaskEntry> cell = ctx.cell();

        ComboBox<String> ownerCombo = new ComboBox<>();
        ownerCombo.setPrefWidth(prefWidth());
        ownerCombo.setMinWidth(prefWidth());
        ownerCombo.setMaxWidth(prefWidth());
        ownerCombo.setPromptText("Owner");
        ownerCombo.getStyleClass().add("task-col-owner");

        ownerCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item);
                setAlignment(Pos.CENTER);
            }
        });
        ownerCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item);
                setAlignment(Pos.CENTER);
            }
        });

        Users.loadUsersIntoComboBox(ownerCombo, Config.getUsersUri(), true);

        ownerCombo.setOnAction(evt -> {
            Helpers.TaskEntry item = cell.getItem();
            if (item == null) return;

            String newOwner = ownerCombo.getValue();
            if (newOwner == null) return;

            boolean wantsAll = ALL.equals(newOwner);
            boolean currentlyAll = (item.owner == null || item.owner.isBlank());
            if (wantsAll && currentlyAll) return;
            if (!wantsAll && newOwner.isBlank()) return;
            if (!wantsAll && newOwner.equals(item.owner)) return;

            ownerCombo.setDisable(true);
            new Thread(() -> {
                try {
                    if (wantsAll) {
                        Tasks.unassignTask(
                                Config.getRequestsUri(),
                                Config.getResponsesUri(),
                                item.listId,
                                item.id
                        );
                    } else {
                        Tasks.assignTask(
                                Config.getRequestsUri(),
                                Config.getResponsesUri(),
                                item.listId,
                                item.id,
                                newOwner
                        );
                    }
                    Platform.runLater(() -> {
                        ownerCombo.setDisable(false);
                        if (ctx.refresh() != null) {
                            ctx.refresh().run();
                        }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> ownerCombo.setDisable(false));
                }
            }, "assign-task").start();
        });

        return new ColumnCell<>() {
            @Override
            public Node node() {
                return ownerCombo;
            }

            @Override
            public void update(Helpers.TaskEntry item) {
                if (item == null) {
                    ownerCombo.setValue(null);
                    return;
                }

                if (item.owner != null && !item.owner.isBlank()) {
                    ownerCombo.setValue(item.owner);
                } else {
                    ownerCombo.setValue(ALL);
                }
            }
        };
    }
}
