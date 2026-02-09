package dk.dtu.collumns;

import dk.dtu.methods.Helpers;
import dk.dtu.methods.Lists;
import dk.dtu.methods.Tasks;
import dk.dtu.shared.Config;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

import java.util.Comparator;

/**
 * Description column usable for both list and task views.
 *
 * Stored as free-form multi-line text and persisted server-side.
 */
public final class Description {

    private Description() {
    }

    public static Column<Helpers.ListEntry> forLists() {
        return new ListDescriptionColumn();
    }

    public static Column<Helpers.TaskEntry> forTasks() {
        return new TaskDescriptionColumn();
    }

    private static final class ListDescriptionColumn implements Column<Helpers.ListEntry> {

        @Override
        public String id() {
            return "description";
        }

        @Override
        public String title() {
            return "Description";
        }

        @Override
        public double prefWidth() {
            return 260;
        }

        @Override
        public Comparator<Helpers.ListEntry> comparator() {
            return Comparator.comparing(e -> e != null ? safe(e.description) : "", String.CASE_INSENSITIVE_ORDER);
        }

        @Override
        public Node createHeader(ColumnHeaderContext<Helpers.ListEntry> ctx) {
            return ColumnUtils.createSortableHeaderLabel(title(), prefWidth(), () -> ctx.onSortRequested().accept(this));
        }

        @Override
        public ColumnCell<Helpers.ListEntry> createCell(ColumnCellContext<Helpers.ListEntry> ctx) {
            ListCell<Helpers.ListEntry> cell = ctx.cell();

            Label preview = new Label();
            preview.setPrefWidth(prefWidth());
            preview.setMinWidth(prefWidth());
            preview.setMaxWidth(prefWidth());
            preview.setAlignment(Pos.CENTER);
            preview.setTextAlignment(TextAlignment.CENTER);
            preview.setWrapText(false);
            preview.setStyle("-fx-cursor: hand;");

            Runnable updatePreview = () -> {
                Helpers.ListEntry item = cell.getItem();
                String text = item != null ? safe(item.description) : "";
                if (text.isBlank()) {
                    preview.setText("Description");
                    preview.setStyle("-fx-cursor: hand; -fx-text-fill: #666;");
                    preview.setTooltip(null);
                } else {
                    String firstLine = text.split("\\R", 2)[0];
                    preview.setText(firstLine);
                    preview.setStyle("-fx-cursor: hand;");
                    preview.setTooltip(new Tooltip(text));
                }
            };

            preview.setOnMouseClicked(evt -> {
                Helpers.ListEntry item = cell.getItem();
                if (item == null) return;
                showDescriptionDialog(
                        "List description",
                        safe(item.name),
                        safe(item.description),
                        desired -> commitList(cell, ctx, desired, updatePreview)
                );
            });

            return new ColumnCell<>() {
                @Override
                public Node node() {
                    return preview;
                }

                @Override
                public void update(Helpers.ListEntry item) {
                    updatePreview.run();
                }
            };
        }

        private static void commitList(
                ListCell<Helpers.ListEntry> cell,
                ColumnCellContext<Helpers.ListEntry> ctx,
                String desired,
                Runnable revert) {

            Helpers.ListEntry item = cell.getItem();
            if (item == null) return;

            String current = safe(item.description);
            if (desired.equals(current)) {
                return;
            }

            new Thread(() -> {
                try {
                    Lists.setListDescription(Config.getRequestsUri(), Config.getResponsesUri(), item.id, desired);
                    Platform.runLater(() -> {
                        // Refresh removed to prevent row shuffling during editing
                        // if (ctx.refresh() != null) {
                        //     ctx.refresh().run();
                        // }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> {
                        revert.run();
                    });
                }
            }, "set-list-description").start();
        }
    }

    private static final class TaskDescriptionColumn implements Column<Helpers.TaskEntry> {

        @Override
        public String id() {
            return "description";
        }

        @Override
        public String title() {
            return "Description";
        }

        @Override
        public double prefWidth() {
            return 260;
        }

        @Override
        public Comparator<Helpers.TaskEntry> comparator() {
            return Comparator.comparing(e -> e != null ? safe(e.description) : "", String.CASE_INSENSITIVE_ORDER);
        }

        @Override
        public Node createHeader(ColumnHeaderContext<Helpers.TaskEntry> ctx) {
            return ColumnUtils.createSortableHeaderLabel(title(), prefWidth(), () -> ctx.onSortRequested().accept(this));
        }

        @Override
        public ColumnCell<Helpers.TaskEntry> createCell(ColumnCellContext<Helpers.TaskEntry> ctx) {
            ListCell<Helpers.TaskEntry> cell = ctx.cell();

            Label preview = new Label();
            preview.setPrefWidth(prefWidth());
            preview.setMinWidth(prefWidth());
            preview.setMaxWidth(prefWidth());
            preview.setAlignment(Pos.CENTER);
            preview.setTextAlignment(TextAlignment.CENTER);
            preview.setWrapText(false);
            preview.setStyle("-fx-cursor: hand;");

            Runnable updatePreview = () -> {
                Helpers.TaskEntry item = cell.getItem();
                String text = item != null ? safe(item.description) : "";
                if (text.isBlank()) {
                    preview.setText("Description");
                    preview.setStyle("-fx-cursor: hand; -fx-text-fill: #666;");
                    preview.setTooltip(null);
                } else {
                    String firstLine = text.split("\\R", 2)[0];
                    preview.setText(firstLine);
                    preview.setStyle("-fx-cursor: hand;");
                    preview.setTooltip(new Tooltip(text));
                }
            };

            preview.setOnMouseClicked(evt -> {
                Helpers.TaskEntry item = cell.getItem();
                if (item == null) return;
                showDescriptionDialog(
                        "Task description",
                        safe(item.title),
                        safe(item.description),
                        desired -> commitTask(cell, ctx, desired, updatePreview)
                );
            });

            return new ColumnCell<>() {
                @Override
                public Node node() {
                    return preview;
                }

                @Override
                public void update(Helpers.TaskEntry item) {
                    updatePreview.run();
                }
            };
        }

        private static void commitTask(
                ListCell<Helpers.TaskEntry> cell,
                ColumnCellContext<Helpers.TaskEntry> ctx,
                String desired,
                Runnable revert) {

            Helpers.TaskEntry item = cell.getItem();
            if (item == null) return;

            String current = safe(item.description);
            if (desired.equals(current)) {
                return;
            }

            new Thread(() -> {
                try {
                    Tasks.setTaskDescription(Config.getRequestsUri(), Config.getResponsesUri(), item.listId, item.id, desired);
                    Platform.runLater(() -> {
                        // Refresh removed to prevent row shuffling during editing
                        // if (ctx.refresh() != null) {
                        //     ctx.refresh().run();
                        // }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> {
                        revert.run();
                    });
                }
            }, "set-task-description").start();
        }
    }

    private interface CommitFn {
        void commit(String desired);
    }

    private static void showDescriptionDialog(String title, String subject, String current, CommitFn commit) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(subject == null || subject.isBlank() ? null : subject);

        TextArea area = new TextArea(current != null ? current : "");
        area.setWrapText(true);
        area.setPrefRowCount(10);
        area.setPrefWidth(520);

        VBox box = new VBox(8, area);
        box.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(box);

        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType clear = new ButtonType("Clear", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(save, clear, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn == clear) {
                commit.commit("");
                return;
            }
            if (btn != save) return;
            commit.commit(safe(area.getText()));
        });
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }
}
