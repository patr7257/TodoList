package dk.dtu.scenes;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dk.dtu.SceneNavigator;
import dk.dtu.SettingsDialog;
import dk.dtu.collumns.*;
import dk.dtu.methods.Helpers;
import dk.dtu.methods.Lists;
import dk.dtu.methods.Tasks;
import dk.dtu.methods.Users;
import dk.dtu.shared.Config;
import dk.dtu.shared.TaskStatus;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.util.Pair;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class D_TodoListView {

    private static final double COLUMN_GAP = 15;

    private enum EmptyFilter { ALL, EMPTY, NOT_EMPTY }

    private static final Gson GSON = new Gson();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {}.getType();

    private static final String PINNED_REORDER_ID = "reorder";
    private static final String PINNED_TITLE_ID = "title";
    private static final String PINNED_DELETE_ID = "delete";

    private final SceneNavigator navigator;
    private final String listId;
    private final String listName;

    private final ListView<Helpers.TaskEntry> tasksView = new ListView<>();
    private List<Helpers.TaskEntry> allTasks = List.of();

    // Filters
    private String ownerFilter = "All";
    private Integer yearFilter = null;
    private Integer priorityFilter = null;
    private TaskStatus statusFilter = null;

    private EmptyFilter titleFilter = EmptyFilter.ALL;
    private EmptyFilter dueDateFilter = EmptyFilter.ALL;
    private EmptyFilter locationFilter = EmptyFilter.ALL;
    private EmptyFilter descriptionFilter = EmptyFilter.ALL;

    // Column state
    private final SortState<Helpers.TaskEntry> sortState = new SortState<>();
    private List<Column<Helpers.TaskEntry>> allTaskColumns;

    // Header node reference (needed for column dialog + reapply)
    private final HBox header = new HBox(COLUMN_GAP);

    // Scroll reference (needed for focus-scroll lock)
    private final AtomicReference<ScrollPane> tableScrollRef = new AtomicReference<>();

    public D_TodoListView(SceneNavigator navigator, String listId, String listName) {
        this.navigator = navigator;
        this.listId = listId;
        this.listName = listName;
    }

    public Scene createScene() {
        Label title = new Label("Tasks in: " + listName);
        title.getStyleClass().add("todolist-title");

        Label info = new Label("List ID: " + listId);
        info.getStyleClass().add("todolist-meta");

        VBox titleSection = new VBox(5, title, info);
        titleSection.setAlignment(Pos.TOP_CENTER);

        allTaskColumns = getAllTaskColumns();

        header.setAlignment(Pos.CENTER_LEFT);
        header.setMaxWidth(Double.MAX_VALUE);
        header.getStyleClass().add("tasks-header");

        tasksView.setPrefHeight(400);
        tasksView.setMaxWidth(Double.MAX_VALUE);
        tasksView.getStyleClass().add("todolist-tasks");

        Runnable refreshTasks = this::reloadTasks;

        // Apply default columns immediately
        applyTaskColumns(header, tasksView, allTaskColumns, sortState, refreshTasks);

        // Initial load
        refreshTasks.run();

        // Apply per-list stored column settings async
        new Thread(() -> {
            try {
                String json = Lists.getTaskColumnsJsonForList(Config.getTodoListsUri(), listId);
                List<String> ids = parseColumnIds(json);
                List<Column<Helpers.TaskEntry>> desired = filterTaskColumnsByIds(allTaskColumns, ids);
                Platform.runLater(() -> applyTaskColumns(header, tasksView, desired, sortState, refreshTasks));
            } catch (Exception ignored) {
                // Keep default columns on error
            }
        }, "load-list-columns").start();

        Hyperlink addTaskLink = new Hyperlink("+  Add new task");
        addTaskLink.getStyleClass().add("create-link");
        addTaskLink.setOnAction(e -> showCreateTaskDialog());

        ScrollPane tableScroll = createScrollableTable(header, tasksView);
        tableScrollRef.set(tableScroll);

        Region spacer = new Region();
        spacer.setMinHeight(8);

        VBox root = new VBox(titleSection, spacer, tableScroll, addTaskLink);
        root.setSpacing(10);
        root.setPadding(new Insets(24));
        root.setAlignment(Pos.TOP_CENTER);
        root.setFillWidth(true);
        root.getStyleClass().add("todolist-root");

        Scene scene = new Scene(root, 900, 600);

        // Fix: prevent focus-induced horizontal jump inside the scroll pane
        lockHorizontalScrollOnFocus(scene, tableScroll);

        return scene;
    }

    // -----------------------------
    // Refresh tasks view when notification received
    // -----------------------------
    public void autoRefreshTasks() {
        reloadTasks();
    }

    private void reloadTasks() {
        Helpers.TaskEntry selected = tasksView.getSelectionModel().getSelectedItem();
        final String previouslySelectedId = (selected != null) ? selected.id : null;

        Tasks.loadTasksForList(Config.getTasksUri(), listId, entries -> {
            allTasks = (entries != null) ? entries : List.of();
            applyTaskFilters();

            if (previouslySelectedId != null) {
                for (Helpers.TaskEntry e : tasksView.getItems()) {
                    if (e != null && previouslySelectedId.equals(e.id)) {
                        tasksView.getSelectionModel().select(e);
                        break;
                    }
                }
            }
        });
    }

    private void applyTaskFilters() {
        List<Helpers.TaskEntry> filtered = new ArrayList<>();
        for (Helpers.TaskEntry e : allTasks) {
            if (e == null) continue;

            if (!matchesEmptyFilter(e.title, titleFilter)) continue;

            if (ownerFilter != null && !"All".equals(ownerFilter)) {
                if (e.owner == null || !ownerFilter.equals(e.owner)) continue;
            }

            if (yearFilter != null && e.year != yearFilter) continue;
            if (priorityFilter != null && e.priority != priorityFilter) continue;

            if (!matchesEmptyFilter(e.dueDate, dueDateFilter)) continue;
            if (!matchesEmptyFilter(e.location, locationFilter)) continue;
            if (!matchesEmptyFilter(e.description, descriptionFilter)) continue;

            if (statusFilter != null) {
                String s = e.status;
                if (s == null || s.isBlank()) continue;
                try {
                    if (TaskStatus.valueOf(s.trim().toUpperCase()) != statusFilter) continue;
                } catch (Exception ex) {
                    continue;
                }
            }

            filtered.add(e);
        }

        tasksView.getItems().setAll(filtered);
    }

    private static boolean matchesEmptyFilter(String value, EmptyFilter filter) {
        if (filter == null || filter == EmptyFilter.ALL) return true;
        boolean empty = value == null || value.isBlank();
        return switch (filter) {
            case EMPTY -> empty;
            case NOT_EMPTY -> !empty;
            default -> true;
        };
    }

    // -----------------------------
    // Filters dialog (kept, just minor cleanup)
    // -----------------------------
    public void openFilterDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Filter tasks");

        ComboBox<String> titleCombo = new ComboBox<>();
        titleCombo.getItems().addAll("All", "Empty", "Not empty");
        titleCombo.setValue(switch (titleFilter) {
            case EMPTY -> "Empty";
            case NOT_EMPTY -> "Not empty";
            default -> "All";
        });

        ComboBox<String> ownerCombo = new ComboBox<>();
        ownerCombo.setPrefWidth(240);
        Users.loadUsersIntoComboBox(ownerCombo, Config.getUsersUri(), true);
        ownerCombo.setValue(ownerFilter != null ? ownerFilter : "All");

        TextField yearField = new TextField(yearFilter != null ? Integer.toString(yearFilter) : "");
        yearField.setPromptText("Year (blank = All)");

        ComboBox<String> priorityCombo = new ComboBox<>();
        priorityCombo.getItems().add("All");
        for (int p = 1; p <= 10; p++) priorityCombo.getItems().add(Integer.toString(p));
        priorityCombo.setValue(priorityFilter != null ? Integer.toString(priorityFilter) : "All");

        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().add("All");
        for (TaskStatus s : TaskStatus.values()) statusCombo.getItems().add(s.name());
        statusCombo.setValue(statusFilter != null ? statusFilter.name() : "All");

        ComboBox<String> dueDateCombo = new ComboBox<>();
        dueDateCombo.getItems().addAll("All", "Empty", "Not empty");
        dueDateCombo.setValue(switch (dueDateFilter) {
            case EMPTY -> "Empty";
            case NOT_EMPTY -> "Not empty";
            default -> "All";
        });

        ComboBox<String> locationCombo = new ComboBox<>();
        locationCombo.getItems().addAll("All", "Empty", "Not empty");
        locationCombo.setValue(switch (locationFilter) {
            case EMPTY -> "Empty";
            case NOT_EMPTY -> "Not empty";
            default -> "All";
        });

        ComboBox<String> descriptionCombo = new ComboBox<>();
        descriptionCombo.getItems().addAll("All", "Empty", "Not empty");
        descriptionCombo.setValue(switch (descriptionFilter) {
            case EMPTY -> "Empty";
            case NOT_EMPTY -> "Not empty";
            default -> "All";
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("Title"), titleCombo);
        grid.addRow(1, new Label("Owner"), ownerCombo);
        grid.addRow(2, new Label("Year"), yearField);
        grid.addRow(3, new Label("Priority"), priorityCombo);
        grid.addRow(4, new Label("Status"), statusCombo);
        grid.addRow(5, new Label("Due date"), dueDateCombo);
        grid.addRow(6, new Label("Location"), locationCombo);
        grid.addRow(7, new Label("Description"), descriptionCombo);

        dialog.getDialogPane().setContent(grid);
        ButtonType apply = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
        ButtonType clear = new ButtonType("Clear", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(apply, clear, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn == clear) {
                ownerFilter = "All";
                yearFilter = null;
                priorityFilter = null;
                statusFilter = null;
                titleFilter = EmptyFilter.ALL;
                dueDateFilter = EmptyFilter.ALL;
                locationFilter = EmptyFilter.ALL;
                descriptionFilter = EmptyFilter.ALL;
                applyTaskFilters();
                return;
            }
            if (btn != apply) return;

            titleFilter = switch (titleCombo.getValue()) {
                case "Empty" -> EmptyFilter.EMPTY;
                case "Not empty" -> EmptyFilter.NOT_EMPTY;
                default -> EmptyFilter.ALL;
            };

            String ownerVal = ownerCombo.getValue();
            if (ownerVal != null) ownerVal = ownerVal.replace(" *", "");
            ownerFilter = (ownerVal == null || ownerVal.isBlank()) ? "All" : ownerVal;

            String yearText = yearField.getText();
            if (yearText == null || yearText.isBlank()) {
                yearFilter = null;
            } else {
                try { yearFilter = Integer.parseInt(yearText.trim()); }
                catch (NumberFormatException ex) { yearFilter = null; }
            }

            String prioVal = priorityCombo.getValue();
            if (prioVal == null || prioVal.isBlank() || "All".equals(prioVal)) {
                priorityFilter = null;
            } else {
                try { priorityFilter = Integer.parseInt(prioVal); }
                catch (NumberFormatException ex) { priorityFilter = null; }
            }

            String statusVal = statusCombo.getValue();
            if (statusVal == null || statusVal.isBlank() || "All".equals(statusVal)) {
                statusFilter = null;
            } else {
                try { statusFilter = TaskStatus.valueOf(statusVal.trim().toUpperCase()); }
                catch (Exception ex) { statusFilter = null; }
            }

            dueDateFilter = switch (dueDateCombo.getValue()) {
                case "Empty" -> EmptyFilter.EMPTY;
                case "Not empty" -> EmptyFilter.NOT_EMPTY;
                default -> EmptyFilter.ALL;
            };
            locationFilter = switch (locationCombo.getValue()) {
                case "Empty" -> EmptyFilter.EMPTY;
                case "Not empty" -> EmptyFilter.NOT_EMPTY;
                default -> EmptyFilter.ALL;
            };
            descriptionFilter = switch (descriptionCombo.getValue()) {
                case "Empty" -> EmptyFilter.EMPTY;
                case "Not empty" -> EmptyFilter.NOT_EMPTY;
                default -> EmptyFilter.ALL;
            };

            applyTaskFilters();
        });
    }

    // -----------------------------
    // Create task
    // -----------------------------
    private void showCreateTaskDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add New Task");
        dialog.setHeaderText("Enter a description for the new task:");
        dialog.setContentText("Task:");

        dialog.showAndWait().ifPresent(name -> {
            if (name == null || name.isBlank()) return;

            new Thread(() -> {
                try {
                    Tasks.addTask(
                            Config.getRequestsUri(),
                            Config.getResponsesUri(),
                            listId,
                            name,
                            "",
                            ""
                    );
                    Platform.runLater(this::reloadTasks);
                } catch (Exception ex) {
                    System.out.println("[CLIENT] ERROR creating task:");
                    ex.printStackTrace();
                }
            }, "create-task").start();
        });
    }

    // -----------------------------
    // Columns
    // -----------------------------
    private List<Column<Helpers.TaskEntry>> getAllTaskColumns() {
        return List.of(
                new TaskReorderColumn(),
                new TaskTitleColumn(),
                dk.dtu.collumns.Priority.forTasks(),
                Year.forTasks(),
                Location.forTasks(),
                Description.forTasks(),
                new TaskStatusColumn(),
                new TaskDueDateColumn(),
                new TaskOwnerColumn(),
                new TaskDeleteColumn()
        );
    }

    private List<String> parseColumnIds(String json) {
        List<String> defaultIds = ensurePinnedTaskColumnOrder(getAllTaskColumns().stream().map(Column::id).toList());
        if (json == null || json.isBlank()) return defaultIds;
        try {
            List<String> parsed = GSON.fromJson(json, STRING_LIST_TYPE);
            if (parsed == null || parsed.isEmpty()) return defaultIds;
            return ensurePinnedTaskColumnOrder(parsed);
        } catch (Exception e) {
            return defaultIds;
        }
    }

    private List<String> ensurePinnedTaskColumnOrder(List<String> ids) {
        List<String> allowedIds = getAllTaskColumns().stream().map(Column::id).toList();
        Set<String> allowed = new LinkedHashSet<>(allowedIds);

        List<String> cleaned = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                if (id == null) continue;
                if (!allowed.contains(id)) continue;
                if (cleaned.contains(id)) continue;
                cleaned.add(id);
            }
        }

        cleaned.remove(PINNED_REORDER_ID);
        cleaned.remove(PINNED_TITLE_ID);
        cleaned.remove(PINNED_DELETE_ID);

        cleaned.add(0, PINNED_TITLE_ID);
        cleaned.add(0, PINNED_REORDER_ID);

        cleaned.add(PINNED_DELETE_ID);
        return cleaned;
    }

    private List<Column<Helpers.TaskEntry>> filterTaskColumnsByIds(List<Column<Helpers.TaskEntry>> all, List<String> ids) {
        List<String> desired = ensurePinnedTaskColumnOrder(ids);

        Map<String, Column<Helpers.TaskEntry>> byId = new HashMap<>();
        for (Column<Helpers.TaskEntry> col : all) byId.put(col.id(), col);

        List<Column<Helpers.TaskEntry>> result = new ArrayList<>();
        for (String id : desired) {
            Column<Helpers.TaskEntry> col = byId.get(id);
            if (col != null) result.add(col);
        }
        if (result.isEmpty()) result.addAll(all);
        return result;
    }

    private void applyTaskColumns(
            HBox header,
            ListView<Helpers.TaskEntry> view,
            List<Column<Helpers.TaskEntry>> visibleColumns,
            SortState<Helpers.TaskEntry> sortState,
            Runnable refreshTasks
    ) {
        Pair<List<Label>, Double> headerBuild = buildHeader(header, view, visibleColumns, sortState);
        double tableWidth = headerBuild.getValue();

        view.setMinWidth(tableWidth);
        view.setPrefWidth(tableWidth);
        view.setMaxWidth(Double.MAX_VALUE);

        view.setCellFactory(lv -> buildTaskRowCell(visibleColumns, refreshTasks));
        view.refresh();
    }

    private Pair<List<Label>, Double> buildHeader(
            HBox header,
            ListView<Helpers.TaskEntry> view,
            List<Column<Helpers.TaskEntry>> visibleColumns,
            SortState<Helpers.TaskEntry> sortState
    ) {
        List<javafx.scene.Node> headerNodes = new ArrayList<>();
        List<Label> headerLabels = new ArrayList<>();

        for (Column<Helpers.TaskEntry> col : visibleColumns) {
            javafx.scene.Node headerNode = col.createHeader(new ColumnHeaderContext<>(
                    view,
                    requested -> sortState.requestSort(requested, view, headerLabels)
            ));

            if (headerNode instanceof Label l) {
                l.setUserData(col);
                headerLabels.add(l);
            }

            StackPane wrapper = wrapCellNode(headerNode, col.prefWidth(), PINNED_TITLE_ID.equals(col.id()));
            headerNodes.add(wrapper);
        }

        header.getChildren().setAll(headerNodes);

        double tableWidth = 0;
        for (Column<Helpers.TaskEntry> col : visibleColumns) tableWidth += col.prefWidth();
        tableWidth += header.getSpacing() * Math.max(0, visibleColumns.size() - 1);

        header.setMinWidth(tableWidth);
        header.setPrefWidth(tableWidth);
        header.setMaxWidth(Double.MAX_VALUE);

        return new Pair<>(headerLabels, tableWidth);
    }

    private ListCell<Helpers.TaskEntry> buildTaskRowCell(List<Column<Helpers.TaskEntry>> visibleColumns, Runnable refreshTasks) {
        return new ListCell<>() {

            private final HBox row;
            private final List<ColumnCell<Helpers.TaskEntry>> cells;
            private javafx.scene.Node reorderHandle;

            {
                cells = new ArrayList<>();
                List<javafx.scene.Node> cellNodes = new ArrayList<>();

                for (Column<Helpers.TaskEntry> col : visibleColumns) {
                    ColumnCell<Helpers.TaskEntry> cellPart = col.createCell(new ColumnCellContext<>(this, refreshTasks));
                    cells.add(cellPart);

                    javafx.scene.Node node = cellPart.node();
                    if (PINNED_REORDER_ID.equals(col.id())) reorderHandle = node;

                    StackPane wrapper = wrapCellNode(node, col.prefWidth(), PINNED_TITLE_ID.equals(col.id()));
                    cellNodes.add(wrapper);
                }

                row = new HBox(COLUMN_GAP, cellNodes.toArray(javafx.scene.Node[]::new));
                row.setAlignment(Pos.CENTER_LEFT);

                setupDragAndDrop();
                setupContextMenu(refreshTasks);
            }

            private void setupDragAndDrop() {
                if (reorderHandle != null) {
                    reorderHandle.setOnDragDetected(evt -> {
                        Helpers.TaskEntry item = getItem();
                        if (item == null) return;

                        Dragboard db = reorderHandle.startDragAndDrop(TransferMode.MOVE);
                        ClipboardContent content = new ClipboardContent();
                        content.putString(item.id);
                        db.setContent(content);

                        try { db.setDragView(row.snapshot(null, null)); } catch (Exception ignored) {}

                        setOpacity(0.4);
                        evt.consume();
                    });
                }

                setOnDragDone(evt -> {
                    setOpacity(1.0);
                    row.setStyle("");
                });

                setOnDragOver(evt -> {
                    Dragboard db = evt.getDragboard();
                    if (db.hasString()) evt.acceptTransferModes(TransferMode.MOVE);
                    evt.consume();
                });

                setOnDragEntered(evt -> {
                    if (evt.getGestureSource() == this) return;
                    if (evt.getDragboard() != null && evt.getDragboard().hasString() && !isEmpty()) {
                        row.setStyle("-fx-background-color: rgba(0, 0, 0, 0.08);");
                    }
                });

                setOnDragExited(evt -> row.setStyle(""));

                setOnDragDropped(evt -> {
                    Dragboard db = evt.getDragboard();
                    if (!db.hasString()) {
                        evt.setDropCompleted(false);
                        evt.consume();
                        return;
                    }

                    ListView<Helpers.TaskEntry> view = getListView();
                    if (view == null) {
                        evt.setDropCompleted(false);
                        evt.consume();
                        return;
                    }

                    String draggedId = db.getString();
                    int draggedIdx = indexOfId(view.getItems(), draggedId);
                    if (draggedIdx < 0) {
                        evt.setDropCompleted(false);
                        evt.consume();
                        return;
                    }

                    int targetIdx = isEmpty() ? view.getItems().size() : Math.max(0, getIndex());
                    Helpers.TaskEntry dragged = view.getItems().remove(draggedIdx);

                    if (targetIdx > view.getItems().size()) targetIdx = view.getItems().size();
                    if (targetIdx > draggedIdx) targetIdx--;

                    view.getItems().add(targetIdx, dragged);
                    view.getSelectionModel().select(dragged);

                    evt.setDropCompleted(true);
                    evt.consume();

                    row.setStyle("");
                    persistTaskOrder(view.getItems());
                });
            }

            private void setupContextMenu(Runnable refreshTasks) {
                ContextMenu contextMenu = new ContextMenu();
                MenuItem renameItem = new MenuItem("Rename task");
                renameItem.setOnAction(evt -> renameTask(refreshTasks));
                contextMenu.getItems().add(renameItem);
                setContextMenu(contextMenu);
            }

            private void renameTask(Runnable refreshTasks) {
                Helpers.TaskEntry item = getItem();
                if (item == null) return;

                TextInputDialog dialog = new TextInputDialog(item.title);
                dialog.setTitle("Rename Task");
                dialog.setHeaderText("Rename task");
                dialog.setContentText("New name:");

                dialog.showAndWait().ifPresent(newTitle -> {
                    if (newTitle == null) return;
                    String trimmed = newTitle.trim();
                    if (trimmed.isBlank() || trimmed.equals(item.title)) return;

                    new Thread(() -> {
                        try {
                            Tasks.renameTask(Config.getRequestsUri(), Config.getResponsesUri(), item.listId, item.id, trimmed);
                            Platform.runLater(refreshTasks);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }, "rename-task").start();
                });
            }

            @Override
            protected void updateItem(Helpers.TaskEntry item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                    setStyle("");
                    return;
                }

                for (ColumnCell<Helpers.TaskEntry> c : cells) c.update(item);

                setGraphic(row);

                if (SettingsDialog.isTintedRowsEnabled() && item.status != null) {
                    String css = getTintColorForStatus(item.status);
                    setStyle(css);
                } else {
                    setStyle("");
                }
            }
        };
    }

    private static StackPane wrapCellNode(javafx.scene.Node node, double prefWidth, boolean grow) {
        StackPane wrapper = new StackPane(node);
        wrapper.setAlignment(Pos.CENTER);

        wrapper.setMinWidth(prefWidth);
        wrapper.setPrefWidth(prefWidth);

        if (grow) {
            wrapper.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(wrapper, javafx.scene.layout.Priority.ALWAYS);
            if (node instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        } else {
            wrapper.setMaxWidth(prefWidth);
        }
        return wrapper;
    }

    private ScrollPane createScrollableTable(HBox header, ListView<Helpers.TaskEntry> tasksView) {
        VBox content = new VBox(0, header, tasksView);
        VBox.setVgrow(tasksView, javafx.scene.layout.Priority.ALWAYS);

        double minTableWidth = header.getMinWidth();
        content.setMinWidth(minTableWidth);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setPannable(true);
        scroll.setFitToWidth(false);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent;");

        scroll.viewportBoundsProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;
            content.setPrefWidth(Math.max(minTableWidth, newVal.getWidth()));
        });

        return scroll;
    }

    private void lockHorizontalScrollOnFocus(Scene scene, ScrollPane tableScroll) {
        scene.focusOwnerProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode == null) return;
            if (tableScroll.getContent() == null) return;
            if (!isDescendantOf(newNode, tableScroll.getContent())) return;

            double keepH = tableScroll.getHvalue();
            Platform.runLater(() -> tableScroll.setHvalue(keepH));
        });
    }

    private static boolean isDescendantOf(javafx.scene.Node node, javafx.scene.Node ancestor) {
        javafx.scene.Node cur = node;
        while (cur != null) {
            if (cur == ancestor) return true;
            cur = cur.getParent();
        }
        return false;
    }

    private static int indexOfId(List<Helpers.TaskEntry> items, String id) {
        for (int i = 0; i < items.size(); i++) {
            Helpers.TaskEntry e = items.get(i);
            if (e != null && Objects.equals(e.id, id)) return i;
        }
        return -1;
    }

    private void persistTaskOrder(List<Helpers.TaskEntry> ordered) {
        List<String> orderedIds = ordered.stream().map(e -> e.id).toList();
        new Thread(() -> {
            try {
                Tasks.setTaskOrderBulk(Config.getRequestsUri(), Config.getResponsesUri(), listId, orderedIds);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }, "persist-task-order").start();
    }

    // -----------------------------
    // Columns dialog
    // -----------------------------
    private void showTaskColumnDialog() {
        List<Column<Helpers.TaskEntry>> all = this.allTaskColumns != null ? this.allTaskColumns : getAllTaskColumns();

        // Determine current visible ids by inspecting the header wrappers' child Label userData (in order)
        List<String> current = new ArrayList<>();
        for (javafx.scene.Node n : header.getChildren()) {
            // header children are StackPane wrappers
            if (!(n instanceof StackPane sp)) continue;
            if (sp.getChildren().isEmpty()) continue;
            javafx.scene.Node child = sp.getChildren().get(0);
            if (child instanceof Label l && l.getUserData() instanceof Column<?> c) {
                @SuppressWarnings("unchecked")
                Column<Helpers.TaskEntry> col = (Column<Helpers.TaskEntry>) c;
                current.add(col.id());
            }
        }
        if (current.isEmpty()) {
            for (Column<Helpers.TaskEntry> col : all) current.add(col.id());
        }
        current = ensurePinnedTaskColumnOrder(current);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Choose columns");
        dialog.setHeaderText("Select which columns to show for this list");

        Map<String, Column<Helpers.TaskEntry>> byId = new HashMap<>();
        for (Column<Helpers.TaskEntry> col : all) byId.put(col.id(), col);

        class Choice {
            final Column<Helpers.TaskEntry> col;
            boolean selected;

            Choice(Column<Helpers.TaskEntry> col, boolean selected) {
                this.col = col;
                this.selected = selected;
            }
        }

        ListView<Choice> choicesView = new ListView<>();
        choicesView.setPrefHeight(260);

        Set<String> currentVisible = new LinkedHashSet<>(current);

        List<String> optionalIds = new ArrayList<>();
        for (String id : current) {
            if (PINNED_REORDER_ID.equals(id) || PINNED_TITLE_ID.equals(id) || PINNED_DELETE_ID.equals(id)) continue;
            if (byId.containsKey(id) && !optionalIds.contains(id)) optionalIds.add(id);
        }
        for (Column<Helpers.TaskEntry> col : all) {
            if (PINNED_REORDER_ID.equals(col.id()) || PINNED_TITLE_ID.equals(col.id()) || PINNED_DELETE_ID.equals(col.id())) continue;
            if (!optionalIds.contains(col.id())) optionalIds.add(col.id());
        }

        for (String id : optionalIds) {
            Column<Helpers.TaskEntry> col = byId.get(id);
            if (col == null) continue;
            choicesView.getItems().add(new Choice(col, currentVisible.contains(id)));
        }

        choicesView.setCellFactory(lv -> new ListCell<>() {
            private final CheckBox cb = new CheckBox();
            private final Label dragHandle = new Label("≡");
            private final HBox row = new HBox(8, dragHandle, cb);

            {
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

                dragHandle.setMinWidth(18);
                dragHandle.setPrefWidth(18);
                dragHandle.setAlignment(Pos.CENTER);
                dragHandle.setStyle("-fx-text-fill: #666; -fx-cursor: open-hand; -fx-font-size: 14px;");

                dragHandle.setOnDragDetected(evt -> {
                    if (getItem() == null) return;
                    Dragboard db = startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent cc = new ClipboardContent();
                    cc.putString(Integer.toString(getIndex()));
                    db.setContent(cc);

                    try { db.setDragView(snapshot(null, null)); } catch (Exception ignored) {}
                    evt.consume();
                });

                setOnDragOver(evt -> {
                    Dragboard db = evt.getDragboard();
                    if (db.hasString()) evt.acceptTransferModes(TransferMode.MOVE);
                    evt.consume();
                });

                setOnDragEntered(evt -> {
                    if (evt.getGestureSource() == this) return;
                    if (evt.getDragboard() != null && evt.getDragboard().hasString() && !isEmpty()) {
                        setStyle("-fx-background-color: rgba(0, 0, 0, 0.08);");
                    }
                });

                setOnDragExited(evt -> setStyle(""));

                setOnDragDropped(evt -> {
                    Dragboard db = evt.getDragboard();
                    if (!db.hasString()) {
                        evt.setDropCompleted(false);
                        evt.consume();
                        return;
                    }

                    int from;
                    try { from = Integer.parseInt(db.getString()); }
                    catch (Exception e) {
                        evt.setDropCompleted(false);
                        evt.consume();
                        return;
                    }

                    int to = getIndex();
                    if (from == to || from < 0 || from >= choicesView.getItems().size()) {
                        evt.setDropCompleted(false);
                        evt.consume();
                        return;
                    }

                    Choice moved = choicesView.getItems().remove(from);
                    if (to > choicesView.getItems().size()) to = choicesView.getItems().size();
                    choicesView.getItems().add(to, moved);

                    evt.setDropCompleted(true);
                    evt.consume();
                });

                setOnDragDone(evt -> setStyle(""));
            }

            @Override
            protected void updateItem(Choice item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                String label = item.col.title().isBlank() ? "Delete" : item.col.title();
                cb.setText(label);
                cb.setSelected(item.selected);
                cb.setOnAction(e -> item.selected = cb.isSelected());
                setGraphic(row);
            }
        });

        Label pinnedNote = new Label("Pinned: reorder handle + task name (always visible) • Delete is always last");
        VBox content = new VBox(10, pinnedNote, choicesView);
        content.setPadding(new Insets(10));

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;

            List<String> selectedIds = new ArrayList<>();
            selectedIds.add(PINNED_REORDER_ID);
            selectedIds.add(PINNED_TITLE_ID);
            for (Choice c : choicesView.getItems()) {
                if (c.selected) selectedIds.add(c.col.id());
            }
            selectedIds.add(PINNED_DELETE_ID);

            String json = GSON.toJson(selectedIds);

            new Thread(() -> {
                try {
                    Lists.setTaskColumnsForList(Config.getRequestsUri(), Config.getResponsesUri(), listId, json);
                    List<Column<Helpers.TaskEntry>> desired = filterTaskColumnsByIds(all, selectedIds);
                    Platform.runLater(() -> applyTaskColumns(header, tasksView, desired, sortState, this::reloadTasks));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }, "save-list-columns").start();
        });
    }

    public void openColumnsDialog() {
        showTaskColumnDialog();
    }

    // Row tints
    private String getTintColorForStatus(String status) {
        if (status == null || status.isBlank()) return "";
        try {
            TaskStatus taskStatus = TaskStatus.valueOf(status.toUpperCase());
            return switch (taskStatus) {
                case NOT_STARTED -> "-fx-background-color: rgba(231, 76, 60, 0.20);";
                case IN_PROGRESS -> "-fx-background-color: rgba(52, 152, 219, 0.20);";
                case DELAYED -> "-fx-background-color: rgba(243, 156, 18, 0.20);";
                case NEED_HELP -> "-fx-background-color: rgba(155, 89, 182, 0.20);";
                case DONE -> "-fx-background-color: rgba(39, 174, 96, 0.20);";
            };
        } catch (Exception e) {
            return "";
        }
    }
}
