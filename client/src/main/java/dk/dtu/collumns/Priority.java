package dk.dtu.collumns;

import dk.dtu.methods.Helpers;
import dk.dtu.methods.Lists;
import dk.dtu.methods.Tasks;
import dk.dtu.shared.Config;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Comparator;

/**
 * Priority column usable for both list and task views.
 * Priority is clamped to 1..10 and persisted server-side.
 */
public final class Priority {

    private Priority() {
    }

    public static Column<Helpers.ListEntry> forLists() {
        return new ListPriorityColumn();
    }

    public static Column<Helpers.TaskEntry> forTasks() {
        return new TaskPriorityColumn();
    }

    private static final class ListPriorityColumn implements Column<Helpers.ListEntry> {

        @Override
        public String id() {
            return "priority";
        }

        @Override
        public String title() {
            return "Priority";
        }

        @Override
        public double prefWidth() {
            return 120;
        }

        @Override
        public Comparator<Helpers.ListEntry> comparator() {
            return Comparator.comparingInt(e -> e != null ? e.priority : 5);
        }

        @Override
        public Node createHeader(ColumnHeaderContext<Helpers.ListEntry> ctx) {
            return ColumnUtils.createSortableHeaderLabel(title(), prefWidth(), () -> ctx.onSortRequested().accept(this));
        }

        @Override
        public ColumnCell<Helpers.ListEntry> createCell(ColumnCellContext<Helpers.ListEntry> ctx) {
            ListCell<Helpers.ListEntry> cell = ctx.cell();

            Button up = createSmallButton("▲");
            Button down = createSmallButton("▼");
            Label value = new Label();
            value.setMinWidth(28);
            value.setAlignment(Pos.CENTER);

            VBox arrows = new VBox(0, up, down);
            arrows.setAlignment(Pos.CENTER);

            HBox box = new HBox(6, value, arrows);
            box.setAlignment(Pos.CENTER);

            box.setPrefWidth(prefWidth() - 10);
            box.setMinWidth(prefWidth() - 10);
            box.setMaxWidth(prefWidth() - 10);

            Runnable updateButtons = () -> {
                Helpers.ListEntry item = cell.getItem();
                int p = (item != null) ? clamp(item.priority) : 5;
                value.setText(Integer.toString(p));
                up.setDisable(item == null || p >= 10);
                down.setDisable(item == null || p <= 1);
            };

            up.setOnAction(evt -> changeListPriority(cell, ctx, +1, up, down, updateButtons));
            down.setOnAction(evt -> changeListPriority(cell, ctx, -1, up, down, updateButtons));

            return new ColumnCell<>() {
                @Override
                public Node node() {
                    return box;
                }

                @Override
                public void update(Helpers.ListEntry item) {
                    updateButtons.run();
                }
            };
        }

        private static void changeListPriority(
                ListCell<Helpers.ListEntry> cell,
                ColumnCellContext<Helpers.ListEntry> ctx,
                int delta,
                Button up,
                Button down,
                Runnable updateButtons) {

            Helpers.ListEntry item = cell.getItem();
            if (item == null) return;

            int next = clamp(item.priority + delta);
            if (next == clamp(item.priority)) return;

            up.setDisable(true);
            down.setDisable(true);

            new Thread(() -> {
                try {
                    Lists.setListPriority(Config.getRequestsUri(), Config.getResponsesUri(), item.id, next);
                    Platform.runLater(() -> {
                        // Refresh removed to prevent row shuffling during editing
                        // if (ctx.refresh() != null) {
                        //     ctx.refresh().run();
                        // }
                        updateButtons.run();
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(updateButtons);
                }
            }, "set-list-priority").start();
        }
    }

    private static final class TaskPriorityColumn implements Column<Helpers.TaskEntry> {

        @Override
        public String id() {
            return "priority";
        }

        @Override
        public String title() {
            return "Priority";
        }

        @Override
        public double prefWidth() {
            return 120;
        }

        @Override
        public Comparator<Helpers.TaskEntry> comparator() {
            return Comparator.comparingInt(e -> e != null ? e.priority : 5);
        }

        @Override
        public Node createHeader(ColumnHeaderContext<Helpers.TaskEntry> ctx) {
            return ColumnUtils.createSortableHeaderLabel(title(), prefWidth(), () -> ctx.onSortRequested().accept(this));
        }

        @Override
        public ColumnCell<Helpers.TaskEntry> createCell(ColumnCellContext<Helpers.TaskEntry> ctx) {
            ListCell<Helpers.TaskEntry> cell = ctx.cell();

            Button up = createSmallButton("▲");
            Button down = createSmallButton("▼");
            Label value = new Label();
            value.setMinWidth(28);
            value.setAlignment(Pos.CENTER);

            VBox arrows = new VBox(0, up, down);
            arrows.setAlignment(Pos.CENTER);

            HBox box = new HBox(6, value, arrows);
            box.setAlignment(Pos.CENTER);

            box.setPrefWidth(prefWidth() - 10);
            box.setMinWidth(prefWidth() - 10);
            box.setMaxWidth(prefWidth() - 10);

            Runnable updateButtons = () -> {
                Helpers.TaskEntry item = cell.getItem();
                int p = (item != null) ? clamp(item.priority) : 5;
                value.setText(Integer.toString(p));
                up.setDisable(item == null || p >= 10);
                down.setDisable(item == null || p <= 1);
            };

            up.setOnAction(evt -> changeTaskPriority(cell, ctx, +1, up, down, updateButtons));
            down.setOnAction(evt -> changeTaskPriority(cell, ctx, -1, up, down, updateButtons));

            return new ColumnCell<>() {
                @Override
                public Node node() {
                    return box;
                }

                @Override
                public void update(Helpers.TaskEntry item) {
                    updateButtons.run();
                }
            };
        }

        private static void changeTaskPriority(
                ListCell<Helpers.TaskEntry> cell,
                ColumnCellContext<Helpers.TaskEntry> ctx,
                int delta,
                Button up,
                Button down,
                Runnable updateButtons) {

            Helpers.TaskEntry item = cell.getItem();
            if (item == null) return;

            int next = clamp(item.priority + delta);
            if (next == clamp(item.priority)) return;

            up.setDisable(true);
            down.setDisable(true);

            new Thread(() -> {
                try {
                    Tasks.setTaskPriority(Config.getRequestsUri(), Config.getResponsesUri(), item.listId, item.id, next);
                    Platform.runLater(() -> {
                        // Refresh removed to prevent row shuffling during editing
                        // if (ctx.refresh() != null) {
                        //     ctx.refresh().run();
                        // }
                        updateButtons.run();
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(updateButtons);
                }
            }, "set-task-priority").start();
        }
    }

    private static Button createSmallButton(String text) {
        Button b = new Button(text);
        b.setFocusTraversable(false);
        b.setPrefSize(22, 14);
        b.setMinSize(22, 14);
        b.setMaxSize(22, 14);
        b.setStyle("-fx-font-size: 9px; -fx-padding: 0;");
        return b;
    }

    private static int clamp(int priority) {
        if (priority < 1) return 1;
        if (priority > 10) return 10;
        return priority;
    }
}
