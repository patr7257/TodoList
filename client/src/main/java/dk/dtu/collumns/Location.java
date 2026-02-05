package dk.dtu.collumns;

import dk.dtu.methods.Helpers;
import dk.dtu.methods.Lists;
import dk.dtu.methods.Tasks;
import dk.dtu.shared.Config;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;

import java.util.Comparator;

/**
 * Location column usable for both list and task views.
 *
 * Stored as a free-form single-line string and persisted server-side.
 */
public final class Location {

    private Location() {
    }

    public static Column<Helpers.ListEntry> forLists() {
        return new ListLocationColumn();
    }

    public static Column<Helpers.TaskEntry> forTasks() {
        return new TaskLocationColumn();
    }

    private static final class ListLocationColumn implements Column<Helpers.ListEntry> {

        @Override
        public String id() {
            return "location";
        }

        @Override
        public String title() {
            return "Location";
        }

        @Override
        public double prefWidth() {
            return 170;
        }

        @Override
        public Comparator<Helpers.ListEntry> comparator() {
            return Comparator.comparing(e -> e != null ? safe(e.location) : "", String.CASE_INSENSITIVE_ORDER);
        }

        @Override
        public Node createHeader(ColumnHeaderContext<Helpers.ListEntry> ctx) {
            return ColumnUtils.createSortableHeaderLabel(title(), prefWidth(), () -> ctx.onSortRequested().accept(this));
        }

        @Override
        public ColumnCell<Helpers.ListEntry> createCell(ColumnCellContext<Helpers.ListEntry> ctx) {
            ListCell<Helpers.ListEntry> cell = ctx.cell();

            TextField field = new TextField();
            field.setPromptText("Location");
            field.setAlignment(Pos.CENTER);
            field.setPrefWidth(prefWidth());
            field.setMinWidth(prefWidth());
            field.setMaxWidth(prefWidth());

            Runnable revert = () -> {
                Helpers.ListEntry item = cell.getItem();
                field.setText(item != null ? safe(item.location) : "");
            };

            field.setOnAction(evt -> commitList(cell, ctx, field, revert));
            field.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (Boolean.FALSE.equals(newVal)) {
                    commitList(cell, ctx, field, revert);
                }
            });

            return new ColumnCell<>() {
                @Override
                public Node node() {
                    return field;
                }

                @Override
                public void update(Helpers.ListEntry item) {
                    revert.run();
                }
            };
        }

        private static void commitList(
                ListCell<Helpers.ListEntry> cell,
                ColumnCellContext<Helpers.ListEntry> ctx,
                TextField field,
                Runnable revert) {

            Helpers.ListEntry item = cell.getItem();
            if (item == null) return;

            String desired = safe(field.getText()).trim();
            String current = safe(item.location);
            if (desired.equals(current)) {
                revert.run();
                return;
            }

            field.setDisable(true);
            new Thread(() -> {
                try {
                    Lists.setListLocation(Config.getRequestsUri(), Config.getResponsesUri(), item.id, desired);
                    Platform.runLater(() -> {
                        field.setDisable(false);
                        if (ctx.refresh() != null) {
                            ctx.refresh().run();
                        }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> {
                        field.setDisable(false);
                        revert.run();
                    });
                }
            }, "set-list-location").start();
        }
    }

    private static final class TaskLocationColumn implements Column<Helpers.TaskEntry> {

        @Override
        public String id() {
            return "location";
        }

        @Override
        public String title() {
            return "Location";
        }

        @Override
        public double prefWidth() {
            return 170;
        }

        @Override
        public Comparator<Helpers.TaskEntry> comparator() {
            return Comparator.comparing(e -> e != null ? safe(e.location) : "", String.CASE_INSENSITIVE_ORDER);
        }

        @Override
        public Node createHeader(ColumnHeaderContext<Helpers.TaskEntry> ctx) {
            return ColumnUtils.createSortableHeaderLabel(title(), prefWidth(), () -> ctx.onSortRequested().accept(this));
        }

        @Override
        public ColumnCell<Helpers.TaskEntry> createCell(ColumnCellContext<Helpers.TaskEntry> ctx) {
            ListCell<Helpers.TaskEntry> cell = ctx.cell();

            TextField field = new TextField();
            field.setPromptText("Location");
            field.setAlignment(Pos.CENTER);
            field.setPrefWidth(prefWidth());
            field.setMinWidth(prefWidth());
            field.setMaxWidth(prefWidth());

            Runnable revert = () -> {
                Helpers.TaskEntry item = cell.getItem();
                field.setText(item != null ? safe(item.location) : "");
            };

            field.setOnAction(evt -> commitTask(cell, ctx, field, revert));
            field.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (Boolean.FALSE.equals(newVal)) {
                    commitTask(cell, ctx, field, revert);
                }
            });

            return new ColumnCell<>() {
                @Override
                public Node node() {
                    return field;
                }

                @Override
                public void update(Helpers.TaskEntry item) {
                    revert.run();
                }
            };
        }

        private static void commitTask(
                ListCell<Helpers.TaskEntry> cell,
                ColumnCellContext<Helpers.TaskEntry> ctx,
                TextField field,
                Runnable revert) {

            Helpers.TaskEntry item = cell.getItem();
            if (item == null) return;

            String desired = safe(field.getText()).trim();
            String current = safe(item.location);
            if (desired.equals(current)) {
                revert.run();
                return;
            }

            field.setDisable(true);
            new Thread(() -> {
                try {
                    Tasks.setTaskLocation(Config.getRequestsUri(), Config.getResponsesUri(), item.listId, item.id, desired);
                    Platform.runLater(() -> {
                        field.setDisable(false);
                        if (ctx.refresh() != null) {
                            ctx.refresh().run();
                        }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> {
                        field.setDisable(false);
                        revert.run();
                    });
                }
            }, "set-task-location").start();
        }
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }
}
