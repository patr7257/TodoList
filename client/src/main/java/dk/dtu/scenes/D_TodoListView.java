package dk.dtu.scenes;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dk.dtu.DarkModeManager;
import dk.dtu.SceneNavigator;
import dk.dtu.SettingsDialog;
import dk.dtu.ViewPrefs;
import dk.dtu.collumns.*;
import dk.dtu.methods.Helpers;
import dk.dtu.methods.Lists;
import dk.dtu.methods.Tasks;
import dk.dtu.methods.Users;
import dk.dtu.shared.Config;
import dk.dtu.shared.TaskStatus;
import dk.dtu.ui.Icons;
import dk.dtu.ui.Tables;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;

import java.lang.reflect.Type;
import java.util.*;

public class D_TodoListView {

    private static final double COLUMN_GAP = 15;

    private enum EmptyFilter { ALL, EMPTY, NOT_EMPTY }

    private static final Gson GSON = new Gson();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {}.getType();

    // View id for the tasks view's per-user-global state (column widths, sort,
    // and filters). The per-list visible-columns choice is stored separately per
    // list via Lists.setTaskColumnsForList / getTaskColumnsJsonForList.
    private static final String VIEW_ID = "tasks";

    private static final String PINNED_REORDER_ID = "reorder";
    private static final String PINNED_TITLE_ID = "title";
    private static final String PINNED_DELETE_ID = "delete";

    private final SceneNavigator navigator;
    private final String listId;
    private final String listName;

    // Backing items for the real TableView.
    private final ObservableList<Helpers.TaskEntry> tableItems = FXCollections.observableArrayList();
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
    private List<Column<Helpers.TaskEntry>> allTaskColumns;

    // The columns currently shown by the table (source of truth for the columns dialog).
    private List<Column<Helpers.TaskEntry>> visibleTaskColumns;

    // Swappable table slot: the columns dialog rebuilds the table into this container.
    private final StackPane tableContainer = new StackPane();
    private TableView<Helpers.TaskEntry> table;

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

        VBox.setVgrow(tableContainer, javafx.scene.layout.Priority.ALWAYS);
        tableContainer.setMaxWidth(Double.MAX_VALUE);

        // Restore saved filters (per user) before the first load so the initial
        // fetch renders already filtered the way the user left it.
        restoreFilters(ViewPrefs.load(VIEW_ID).filters);

        // Build the table with the default columns immediately.
        rebuildTable(allTaskColumns);

        // Initial load
        reloadTasks();

        // Apply per-list stored column settings async
        new Thread(() -> {
            try {
                String json = Lists.getTaskColumnsJsonForList(Config.getTodoListsUri(), listId);
                List<String> ids = parseColumnIds(json);
                List<Column<Helpers.TaskEntry>> desired = filterTaskColumnsByIds(allTaskColumns, ids);
                Platform.runLater(() -> rebuildTable(desired));
            } catch (Exception ignored) {
                // Keep default columns on error
            }
        }, "load-list-columns").start();

        Hyperlink addTaskLink = new Hyperlink("+  Add new task");
        addTaskLink.getStyleClass().add("create-link");
        addTaskLink.setOnAction(e -> showCreateTaskDialog());

        // Auto-fit columns to their content's optimal width (acts on the current table).
        javafx.scene.control.Button autoFitButton = new javafx.scene.control.Button("Auto-fit columns");
        autoFitButton.setGraphic(dk.dtu.ui.Icons.of("fth-maximize-2", 14));
        autoFitButton.getStyleClass().addAll(atlantafx.base.theme.Styles.FLAT, "autofit-button");
        autoFitButton.setOnAction(e -> dk.dtu.ui.Tables.autoFitColumns(table));

        javafx.scene.layout.HBox footer = new javafx.scene.layout.HBox(24, addTaskLink, autoFitButton);
        footer.setAlignment(Pos.CENTER);

        Region spacer = new Region();
        spacer.setMinHeight(8);

        VBox root = new VBox(titleSection, spacer, tableContainer, footer);
        root.setSpacing(10);
        root.setPadding(new Insets(24));
        root.setAlignment(Pos.TOP_CENTER);
        root.setFillWidth(true);
        root.getStyleClass().add("todolist-root");

        return new Scene(root, 900, 600);
    }

    // -----------------------------
    // Build / rebuild the real TableView
    // -----------------------------
    private void rebuildTable(List<Column<Helpers.TaskEntry>> visibleColumns) {
        this.visibleTaskColumns = visibleColumns;

        Runnable refreshTasks = this::reloadTasks;

        Tables.Config<Helpers.TaskEntry> cfg = Tables.<Helpers.TaskEntry>config()
                .idOf(e -> e.id)
                .persistOrder(this::persistTaskOrder)
                .tintStyle(e -> getTintColorForStatus(e.status))
                .tintEnabled(SettingsDialog::isTintedRowsEnabled)
                .contextMenu(this::buildRowMenu)
                .refresh(refreshTasks);

        table = Tables.build(visibleColumns, tableItems, cfg);
        table.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);

        // Restore saved column widths + sort, then wire auto-save (resize, header
        // reorder, sort) so every change persists. Restore runs before binding so
        // it does not echo back as a save.
        List<String> knownIds = getAllTaskColumns().stream().map(Column::id).toList();
        ViewPrefs.ViewState state = ViewPrefs.load(VIEW_ID).sanitized(knownIds);
        Tables.applyState(table, state.widths, state.sortColumn, state.sortAscending);
        Tables.bindAutoSave(table, this::persistTableState);

        tableContainer.getChildren().setAll(table);
    }

    /**
     * Persist the tasks view state after a change: column widths + sort go to the
     * per-user-global "tasks" profile; the current column order (visibility +
     * order) goes to this list's local column store.
     */
    private void persistTableState() {
        if (table == null) return;
        ViewPrefs.ViewState s = ViewPrefs.load(VIEW_ID);
        s.widths = Tables.columnWidths(table);
        s.sortColumn = Tables.sortColumnId(table);
        s.sortAscending = Tables.sortAscending(table);
        ViewPrefs.save(VIEW_ID, s);

        List<String> orderedIds = ensurePinnedTaskColumnOrder(Tables.columnOrder(table));
        try {
            Lists.setTaskColumnsForList(Config.getRequestsUri(), Config.getResponsesUri(),
                    listId, GSON.toJson(orderedIds));
        } catch (Exception ignored) {
            // local write; ignore failures rather than break the view
        }
    }

    // -----------------------------
    // Filter persistence (per user, tasks view)
    // -----------------------------

    private void saveFilters() {
        ViewPrefs.ViewState s = ViewPrefs.load(VIEW_ID);
        s.filters = captureFilters();
        ViewPrefs.save(VIEW_ID, s);
    }

    private Map<String, String> captureFilters() {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("owner", ownerFilter == null ? "All" : ownerFilter);
        f.put("year", yearFilter == null ? "" : Integer.toString(yearFilter));
        f.put("priority", priorityFilter == null ? "" : Integer.toString(priorityFilter));
        f.put("status", statusFilter == null ? "" : statusFilter.name());
        f.put("title", titleFilter.name());
        f.put("dueDate", dueDateFilter.name());
        f.put("location", locationFilter.name());
        f.put("description", descriptionFilter.name());
        return f;
    }

    private void restoreFilters(Map<String, String> f) {
        if (f == null || f.isEmpty()) return;
        ownerFilter = f.getOrDefault("owner", "All");
        yearFilter = Helpers.parseIntOrNull(f.get("year"));
        priorityFilter = Helpers.parseIntOrNull(f.get("priority"));
        statusFilter = Helpers.parseEnum(TaskStatus.class, f.get("status"), null);
        titleFilter = Helpers.parseEnum(EmptyFilter.class, f.get("title"), EmptyFilter.ALL);
        dueDateFilter = Helpers.parseEnum(EmptyFilter.class, f.get("dueDate"), EmptyFilter.ALL);
        locationFilter = Helpers.parseEnum(EmptyFilter.class, f.get("location"), EmptyFilter.ALL);
        descriptionFilter = Helpers.parseEnum(EmptyFilter.class, f.get("description"), EmptyFilter.ALL);
    }

    // Right-click row menu: "Rename task" for THAT entry.
    private ContextMenu buildRowMenu(Helpers.TaskEntry entry) {
        ContextMenu menu = new ContextMenu();
        MenuItem renameItem = new MenuItem("Rename task");
        renameItem.setOnAction(evt -> renameTask(entry));
        menu.getItems().add(renameItem);
        return menu;
    }

    private void renameTask(Helpers.TaskEntry item) {
        if (item == null) return;

        TextInputDialog dialog = new TextInputDialog(item.title);
        DarkModeManager.prepareDialog(dialog, DarkModeManager.windowOf(tableContainer));
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
                    Platform.runLater(this::reloadTasks);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }, "rename-task").start();
        });
    }

    // -----------------------------
    // Refresh tasks view when notification received
    // -----------------------------
    public void autoRefreshTasks() {
        reloadTasks();
    }

    private void reloadTasks() {
        // Refresh the shared user list once per reload (owner dropdowns read it).
        dk.dtu.methods.Users.invalidateUserCache();

        Helpers.TaskEntry selected = (table != null) ? table.getSelectionModel().getSelectedItem() : null;
        final String previouslySelectedId = (selected != null) ? selected.id : null;

        Tasks.loadTasksForList(Config.getTasksUri(), listId, entries -> {
            allTasks = (entries != null) ? entries : List.of();
            applyTaskFilters();

            if (previouslySelectedId != null && table != null) {
                for (Helpers.TaskEntry e : tableItems) {
                    if (e != null && previouslySelectedId.equals(e.id)) {
                        table.getSelectionModel().select(e);
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

        tableItems.setAll(filtered);
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
        DarkModeManager.prepareDialog(dialog, DarkModeManager.windowOf(tableContainer));
        dialog.setTitle("Filter tasks");

        final double FILTER_CONTROL_WIDTH = 240;

        ComboBox<String> titleCombo = new ComboBox<>();
        titleCombo.getItems().addAll("All", "Empty", "Not empty");
        titleCombo.setPrefWidth(FILTER_CONTROL_WIDTH);
        titleCombo.setMinWidth(FILTER_CONTROL_WIDTH);
        titleCombo.setValue(switch (titleFilter) {
            case EMPTY -> "Empty";
            case NOT_EMPTY -> "Not empty";
            default -> "All";
        });

        ComboBox<String> ownerCombo = new ComboBox<>();
        ownerCombo.setPrefWidth(FILTER_CONTROL_WIDTH);
        ownerCombo.setMinWidth(FILTER_CONTROL_WIDTH);
        Users.loadUsersIntoComboBox(ownerCombo, Config.getUsersUri(), true);
        ownerCombo.setValue(ownerFilter != null ? ownerFilter : "All");

        TextField yearField = new TextField(yearFilter != null ? Integer.toString(yearFilter) : "");
        yearField.setPrefWidth(FILTER_CONTROL_WIDTH);
        yearField.setMinWidth(FILTER_CONTROL_WIDTH);
        yearField.setPromptText("Year (blank = All)");

        ComboBox<String> priorityCombo = new ComboBox<>();
        priorityCombo.getItems().add("All");
        for (int p = 1; p <= 10; p++) priorityCombo.getItems().add(Integer.toString(p));
        priorityCombo.setPrefWidth(FILTER_CONTROL_WIDTH);
        priorityCombo.setMinWidth(FILTER_CONTROL_WIDTH);
        priorityCombo.setValue(priorityFilter != null ? Integer.toString(priorityFilter) : "All");

        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().add("All");
        for (TaskStatus s : TaskStatus.values()) statusCombo.getItems().add(s.name());
        statusCombo.setPrefWidth(FILTER_CONTROL_WIDTH);
        statusCombo.setMinWidth(FILTER_CONTROL_WIDTH);
        statusCombo.setValue(statusFilter != null ? statusFilter.name() : "All");

        ComboBox<String> dueDateCombo = new ComboBox<>();
        dueDateCombo.getItems().addAll("All", "Empty", "Not empty");
        dueDateCombo.setPrefWidth(FILTER_CONTROL_WIDTH);
        dueDateCombo.setMinWidth(FILTER_CONTROL_WIDTH);
        dueDateCombo.setValue(switch (dueDateFilter) {
            case EMPTY -> "Empty";
            case NOT_EMPTY -> "Not empty";
            default -> "All";
        });

        ComboBox<String> locationCombo = new ComboBox<>();
        locationCombo.getItems().addAll("All", "Empty", "Not empty");
        locationCombo.setPrefWidth(FILTER_CONTROL_WIDTH);
        locationCombo.setMinWidth(FILTER_CONTROL_WIDTH);
        locationCombo.setValue(switch (locationFilter) {
            case EMPTY -> "Empty";
            case NOT_EMPTY -> "Not empty";
            default -> "All";
        });

        ComboBox<String> descriptionCombo = new ComboBox<>();
        descriptionCombo.getItems().addAll("All", "Empty", "Not empty");
        descriptionCombo.setPrefWidth(FILTER_CONTROL_WIDTH);
        descriptionCombo.setMinWidth(FILTER_CONTROL_WIDTH);
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
                saveFilters();
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
            saveFilters();
        });
    }

    // -----------------------------
    // Create task
    // -----------------------------
    private void showCreateTaskDialog() {
        TextInputDialog dialog = new TextInputDialog();
        DarkModeManager.prepareDialog(dialog, DarkModeManager.windowOf(tableContainer));
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

        // Current visible ids come from the tracked visible columns, not header labels.
        List<String> current = new ArrayList<>();
        if (visibleTaskColumns != null) {
            for (Column<Helpers.TaskEntry> col : visibleTaskColumns) current.add(col.id());
        }
        if (current.isEmpty()) {
            for (Column<Helpers.TaskEntry> col : all) current.add(col.id());
        }
        current = ensurePinnedTaskColumnOrder(current);

        Dialog<ButtonType> dialog = new Dialog<>();
        DarkModeManager.prepareDialog(dialog, DarkModeManager.windowOf(tableContainer));
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
            private final Label dragHandle = new Label();
            private final HBox row = new HBox(8, dragHandle, cb);

            {
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

                dragHandle.setGraphic(Icons.reorder());
                dragHandle.setMinWidth(18);
                dragHandle.setPrefWidth(18);
                dragHandle.setAlignment(Pos.CENTER);
                dragHandle.getStyleClass().add("reorder-handle");

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
                        if (!getStyleClass().contains("drag-over")) getStyleClass().add("drag-over");
                    }
                });

                setOnDragExited(evt -> getStyleClass().remove("drag-over"));

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

                setOnDragDone(evt -> getStyleClass().remove("drag-over"));
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
                    Platform.runLater(() -> rebuildTable(desired));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }, "save-list-columns").start();
        });
    }

    public void openColumnsDialog() {
        showTaskColumnDialog();
    }

    // Row tints. The looked-up -status-*-bg colors resolve per theme
    // (common.css light tokens, theme-warm-dark.css dark tokens).
    private String getTintColorForStatus(String status) {
        if (status == null || status.isBlank()) return "";
        try {
            TaskStatus taskStatus = TaskStatus.valueOf(status.toUpperCase());
            return switch (taskStatus) {
                case NOT_STARTED -> "-fx-background-color: -status-todo-bg;";
                case IN_PROGRESS -> "-fx-background-color: -status-prog-bg;";
                case DELAYED -> "-fx-background-color: -status-late-bg;";
                case NEED_HELP -> "-fx-background-color: -status-help-bg;";
                case DONE -> "-fx-background-color: -status-done-bg;";
            };
        } catch (Exception e) {
            return "";
        }
    }
}
