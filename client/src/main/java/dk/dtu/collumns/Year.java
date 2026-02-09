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
 * Year column usable for both list and task views.
 *
 * Year is stored as an integer (0 means unset) and persisted server-side.
 */
public final class Year {

    private Year() {
    }

    public static Column<Helpers.ListEntry> forLists() {
        return new ListYearColumn();
    }

    public static Column<Helpers.TaskEntry> forTasks() {
        return new TaskYearColumn();
    }

    private static final class ListYearColumn implements Column<Helpers.ListEntry> {

        @Override
        public String id() {
            return "year";
        }

        @Override
        public String title() {
            return "Year";
        }

        @Override
        public double prefWidth() {
            return 110;
        }

        @Override
        public Comparator<Helpers.ListEntry> comparator() {
            return Comparator.comparingInt(e -> {
                int year = (e != null) ? e.year : 0;
                return year <= 0 ? Integer.MAX_VALUE : year;
            });
        }

        @Override
        public Node createHeader(ColumnHeaderContext<Helpers.ListEntry> ctx) {
            return ColumnUtils.createSortableHeaderLabel(title(), prefWidth(), () -> ctx.onSortRequested().accept(this));
        }

        @Override
        public ColumnCell<Helpers.ListEntry> createCell(ColumnCellContext<Helpers.ListEntry> ctx) {
            ListCell<Helpers.ListEntry> cell = ctx.cell();

            TextField field = new TextField();
            field.setPromptText("Year");
            field.setAlignment(Pos.CENTER);
            field.setPrefWidth(prefWidth() - 10);
            field.setMinWidth(prefWidth() - 10);
            field.setMaxWidth(prefWidth() - 10);

            Runnable revert = () -> {
                Helpers.ListEntry item = cell.getItem();
                if (item == null) {
                    field.setText("");
                } else {
                    field.setText(item.year > 0 ? Integer.toString(item.year) : "");
                }
            };

            field.setOnAction(evt -> commitListYear(cell, ctx, field, revert));
            field.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (Boolean.FALSE.equals(newVal)) {
                    commitListYear(cell, ctx, field, revert);
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

        private static void commitListYear(
                ListCell<Helpers.ListEntry> cell,
                ColumnCellContext<Helpers.ListEntry> ctx,
                TextField field,
                Runnable revert) {

            Helpers.ListEntry item = cell.getItem();
            if (item == null) return;

            int desired;
            String raw = field.getText() != null ? field.getText().trim() : "";
            if (raw.isBlank()) {
                desired = 0;
            } else {
                try {
                    desired = Integer.parseInt(raw);
                } catch (Exception ex) {
                    revert.run();
                    return;
                }
            }
            desired = clampYear(desired);

            if (desired == item.year) {
                revert.run();
                return;
            }

            field.setDisable(true);
            final int next = desired;
            new Thread(() -> {
                try {
                    Lists.setListYear(Config.getRequestsUri(), Config.getResponsesUri(), item.id, next);
                    Platform.runLater(() -> {
                        field.setDisable(false);
                        // Refresh removed to prevent row shuffling during editing
                        // if (ctx.refresh() != null) {
                        //     ctx.refresh().run();
                        // }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> {
                        field.setDisable(false);
                        revert.run();
                    });
                }
            }, "set-list-year").start();
        }
    }

    private static final class TaskYearColumn implements Column<Helpers.TaskEntry> {

        @Override
        public String id() {
            return "year";
        }

        @Override
        public String title() {
            return "Year";
        }

        @Override
        public double prefWidth() {
            return 110;
        }

        @Override
        public Comparator<Helpers.TaskEntry> comparator() {
            return Comparator.comparingInt(e -> {
                int year = (e != null) ? e.year : 0;
                return year <= 0 ? Integer.MAX_VALUE : year;
            });
        }

        @Override
        public Node createHeader(ColumnHeaderContext<Helpers.TaskEntry> ctx) {
            return ColumnUtils.createSortableHeaderLabel(title(), prefWidth(), () -> ctx.onSortRequested().accept(this));
        }

        @Override
        public ColumnCell<Helpers.TaskEntry> createCell(ColumnCellContext<Helpers.TaskEntry> ctx) {
            ListCell<Helpers.TaskEntry> cell = ctx.cell();

            TextField field = new TextField();
            field.setPromptText("Year");
            field.setAlignment(Pos.CENTER);
            field.setPrefWidth(prefWidth() - 10);
            field.setMinWidth(prefWidth() - 10);
            field.setMaxWidth(prefWidth() - 10);

            Runnable revert = () -> {
                Helpers.TaskEntry item = cell.getItem();
                if (item == null) {
                    field.setText("");
                } else {
                    field.setText(item.year > 0 ? Integer.toString(item.year) : "");
                }
            };

            field.setOnAction(evt -> commitTaskYear(cell, ctx, field, revert));
            field.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (Boolean.FALSE.equals(newVal)) {
                    commitTaskYear(cell, ctx, field, revert);
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

        private static void commitTaskYear(
                ListCell<Helpers.TaskEntry> cell,
                ColumnCellContext<Helpers.TaskEntry> ctx,
                TextField field,
                Runnable revert) {

            Helpers.TaskEntry item = cell.getItem();
            if (item == null) return;

            int desired;
            String raw = field.getText() != null ? field.getText().trim() : "";
            if (raw.isBlank()) {
                desired = 0;
            } else {
                try {
                    desired = Integer.parseInt(raw);
                } catch (Exception ex) {
                    revert.run();
                    return;
                }
            }
            desired = clampYear(desired);

            if (desired == item.year) {
                revert.run();
                return;
            }

            field.setDisable(true);
            final int next = desired;
            new Thread(() -> {
                try {
                    Tasks.setTaskYear(Config.getRequestsUri(), Config.getResponsesUri(), item.listId, item.id, next);
                    Platform.runLater(() -> {
                        field.setDisable(false);
                        // Refresh removed to prevent row shuffling during editing
                        // if (ctx.refresh() != null) {
                        //     ctx.refresh().run();
                        // }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> {
                        field.setDisable(false);
                        revert.run();
                    });
                }
            }, "set-task-year").start();
        }
    }

    private static int clampYear(int value) {
        if (value < 0) return 0;
        if (value > 9999) return 9999;
        return value;
    }
}
