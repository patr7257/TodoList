package dk.dtu.scenes;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dk.dtu.SceneNavigator;
import dk.dtu.collumns.*;
import dk.dtu.methods.Helpers;
import dk.dtu.methods.Lists;
import dk.dtu.methods.Users;
import dk.dtu.shared.Config;
import dk.dtu.ui.Icons;
import dk.dtu.ui.Tables;
import javafx.animation.PauseTransition;
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
import javafx.util.Duration;

import java.lang.reflect.Type;
import java.util.*;
import java.util.prefs.Preferences;

public class C_MainMenu {

    private enum EmptyFilter { ALL, EMPTY, NOT_EMPTY }
    private enum YesNoFilter { ALL, YES, NO }
    private enum CompletionFilter { ALL, COMPLETED, NOT_COMPLETED }

    private static final Gson GSON = new Gson();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {}.getType();
    private static final String PREF_LIST_COLUMNS_JSON = "mainMenu.listColumnsJson";

    private static final String PINNED_REORDER_ID = "reorder";
    private static final String PINNED_NAME_ID = "name";
    private static final String PINNED_DELETE_ID = "delete";

    private final SceneNavigator navigator;
    private final String loginMessage;

    private final ObservableList<Helpers.ListEntry> tableItems = FXCollections.observableArrayList();
    private TableView<Helpers.ListEntry> table;
    private List<Helpers.ListEntry> allLists = List.of();

    private String ownerFilter = "All";
    private Integer yearFilter = null;
    private Integer priorityFilter = null;
    private CompletionFilter completionFilter = CompletionFilter.ALL;

    private EmptyFilter nameFilter = EmptyFilter.ALL;
    private EmptyFilter locationFilter = EmptyFilter.ALL;
    private EmptyFilter descriptionFilter = EmptyFilter.ALL;
    private YesNoFilter hasTasksFilter = YesNoFilter.ALL;
    private YesNoFilter overdueFilter = YesNoFilter.ALL;

    public C_MainMenu(SceneNavigator navigator) {
        this(navigator, null);
    }

    public C_MainMenu(SceneNavigator navigator, String loginMessage) {
        this.navigator = navigator;
        this.loginMessage = loginMessage;
    }

    public Scene createScene() {
        // Title + user info
        Label title = new Label("Available todo lists");
        title.getStyleClass().add("mainmenu-title");

        Label userLabel = new Label("Logged in as: " + navigator.getCurrentUser());

        Label tempMessageLabel = new Label();
        setupLoginMessage(tempMessageLabel);

        VBox titleSection = new VBox(5, title, userLabel, tempMessageLabel);
        titleSection.setAlignment(Pos.TOP_CENTER);

        // Refresh hook used by the delete cell and rename flow
        Runnable refreshLists = this::reloadLists;

        // Real TableView built from the existing visible columns via the shared adapter
        Tables.Config<Helpers.ListEntry> cfg = Tables.<Helpers.ListEntry>config()
                .idOf(e -> e.id)
                .persistOrder(this::persistListOrder)
                .onOpen(e -> navigator.showTodoList(e.id, e.name))
                .contextMenu(this::buildRowMenu)
                .refresh(refreshLists);

        table = Tables.build(getVisibleListColumns(), tableItems, cfg);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);

        // Load lists once initially
        refreshLists.run();

        // Create list link
        Hyperlink createLink = new Hyperlink("+  Create new list");
        createLink.getStyleClass().add("create-link");
        createLink.setOnAction(e -> showCreateListDialog());

        // Auto-fit columns to their content's optimal width.
        javafx.scene.control.Button autoFitButton = new javafx.scene.control.Button("Auto-fit columns");
        autoFitButton.setGraphic(dk.dtu.ui.Icons.of("fth-maximize-2", 14));
        autoFitButton.getStyleClass().addAll(atlantafx.base.theme.Styles.FLAT, "autofit-button");
        autoFitButton.setOnAction(e -> dk.dtu.ui.Tables.autoFitColumns(table));

        javafx.scene.layout.HBox footer = new javafx.scene.layout.HBox(24, createLink, autoFitButton);
        footer.setAlignment(Pos.CENTER);

        // Spacer between title and table (instead of dummy Label(""))
        Region spacer = new Region();
        spacer.setMinHeight(8);

        VBox root = new VBox(titleSection, spacer, table, footer);
        root.setSpacing(10);
        root.setPadding(new Insets(24));
        root.setAlignment(Pos.TOP_CENTER);
        root.setFillWidth(true);
        root.getStyleClass().add("mainmenu-root");

        return new Scene(root, 900, 600);
    }

    /** Row context menu: a single "Rename list" action bound to the given entry. */
    private ContextMenu buildRowMenu(Helpers.ListEntry item) {
        ContextMenu menu = new ContextMenu();
        MenuItem renameItem = new MenuItem("Rename list");
        renameItem.setOnAction(evt -> renameSelectedList(item));
        menu.getItems().add(renameItem);
        return menu;
    }

    private void renameSelectedList(Helpers.ListEntry item) {
        if (item == null) return;

        TextInputDialog dialog = new TextInputDialog(item.name);
        dialog.setTitle("Rename List");
        dialog.setHeaderText("Rename list");
        dialog.setContentText("New name:");

        dialog.showAndWait().ifPresent(newName -> {
            if (newName == null) return;
            String trimmed = newName.trim();
            if (trimmed.isBlank() || trimmed.equals(item.name)) return;

            new Thread(() -> {
                try {
                    Lists.renameTodoList(Config.getRequestsUri(), Config.getResponsesUri(), item.id, trimmed);
                    Platform.runLater(this::reloadLists);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }, "rename-list").start();
        });
    }

    // -----------------------------
    // UI helpers
    // -----------------------------

    private void setupLoginMessage(Label tempMessageLabel) {
        if (loginMessage == null || loginMessage.isBlank()) return;

        tempMessageLabel.setText(loginMessage);
        if (!tempMessageLabel.getStyleClass().contains("status-connected")) {
            tempMessageLabel.getStyleClass().add("status-connected");
        }
        // font-weight is emphasis, not a theme color, so it stays inline
        tempMessageLabel.setStyle("-fx-font-weight: bold;");

        PauseTransition pt = new PauseTransition(Duration.seconds(2));
        pt.setOnFinished(e -> {
            tempMessageLabel.setText("");
            tempMessageLabel.getStyleClass().remove("status-connected");
            tempMessageLabel.setStyle("");
        });
        pt.play();
    }

    private void persistListOrder(List<Helpers.ListEntry> ordered) {
        List<String> orderedIds = ordered.stream().map(e -> e.id).toList();
        new Thread(() -> {
            try {
                Lists.setListOrderBulk(Config.getRequestsUri(), Config.getResponsesUri(), orderedIds);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }, "persist-list-order").start();
    }

    // -----------------------------
    // Data actions
    // -----------------------------

    private void showCreateListDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create New To Do List");
        dialog.setHeaderText("Enter name for the new list:");
        dialog.setContentText("Name:");

        dialog.showAndWait().ifPresent(name -> {
            if (name == null || name.isBlank()) return;

            new Thread(() -> {
                try {
                    Lists.createTodoList(
                            Config.getRequestsUri(),
                            Config.getResponsesUri(),
                            name,
                            navigator.getCurrentUser()
                    );
                    Platform.runLater(this::reloadLists);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }, "create-list").start();
        });
    }

    /**
     * Auto-refresh lists when notification received from server.
     * Called by SceneNavigator when server broadcasts list changes.
     */
    public void autoRefreshLists() {
        reloadLists();
    }

    private void reloadLists() {
        // Refresh the shared user list once per reload (owner dropdowns read it).
        dk.dtu.methods.Users.invalidateUserCache();

        Helpers.ListEntry selected = (table != null) ? table.getSelectionModel().getSelectedItem() : null;
        final String previouslySelectedId = (selected != null) ? selected.id : null;

        Lists.loadTodoLists(Config.getTodoListsUri(), entries -> {
            allLists = (entries != null) ? entries : List.of();
            applyListFilters();

            if (previouslySelectedId != null && table != null) {
                for (Helpers.ListEntry e : tableItems) {
                    if (e != null && previouslySelectedId.equals(e.id)) {
                        table.getSelectionModel().select(e);
                        break;
                    }
                }
            }
        });
    }

    private void applyListFilters() {
        List<Helpers.ListEntry> filtered = new ArrayList<>();
        for (Helpers.ListEntry e : allLists) {
            if (e == null) continue;

            if (!matchesEmptyFilter(e.name, nameFilter)) continue;

            if (ownerFilter != null && !"All".equals(ownerFilter)) {
                if (e.owner == null || !ownerFilter.equals(e.owner)) continue;
            }

            if (yearFilter != null && e.year != yearFilter) continue;
            if (priorityFilter != null && e.priority != priorityFilter) continue;

            if (completionFilter == CompletionFilter.COMPLETED && e.completionPercentage < 100) continue;
            if (completionFilter == CompletionFilter.NOT_COMPLETED && e.completionPercentage >= 100) continue;

            if (!matchesEmptyFilter(e.location, locationFilter)) continue;
            if (!matchesEmptyFilter(e.description, descriptionFilter)) continue;

            if (hasTasksFilter == YesNoFilter.YES && e.taskCount <= 0) continue;
            if (hasTasksFilter == YesNoFilter.NO && e.taskCount > 0) continue;

            boolean isOverdue = e.overdueTaskCount > 0;
            if (overdueFilter == YesNoFilter.YES && !isOverdue) continue;
            if (overdueFilter == YesNoFilter.NO && isOverdue) continue;

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
    // Filters dialog (kept as-is, just lightly tidied)
    // -----------------------------

    public void openFilterDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Filter lists");

        ComboBox<String> nameCombo = new ComboBox<>();
        nameCombo.getItems().addAll("All", "Empty", "Not empty");
        nameCombo.setValue(switch (nameFilter) {
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

        ComboBox<String> completionCombo = new ComboBox<>();
        completionCombo.getItems().addAll("All", "Completed", "Not completed");
        completionCombo.setValue(switch (completionFilter) {
            case COMPLETED -> "Completed";
            case NOT_COMPLETED -> "Not completed";
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

        ComboBox<String> hasTasksCombo = new ComboBox<>();
        hasTasksCombo.getItems().addAll("All", "Has tasks", "No tasks");
        hasTasksCombo.setValue(switch (hasTasksFilter) {
            case YES -> "Has tasks";
            case NO -> "No tasks";
            default -> "All";
        });

        ComboBox<String> overdueCombo = new ComboBox<>();
        overdueCombo.getItems().addAll("All", "Overdue", "Not overdue");
        overdueCombo.setValue(switch (overdueFilter) {
            case YES -> "Overdue";
            case NO -> "Not overdue";
            default -> "All";
        });

        double comboWidth = 200;
        nameCombo.setPrefWidth(comboWidth);
        ownerCombo.setPrefWidth(comboWidth);
        yearField.setPrefWidth(comboWidth);
        priorityCombo.setPrefWidth(comboWidth);
        completionCombo.setPrefWidth(comboWidth);
        locationCombo.setPrefWidth(comboWidth);
        descriptionCombo.setPrefWidth(comboWidth);
        hasTasksCombo.setPrefWidth(comboWidth);
        overdueCombo.setPrefWidth(comboWidth);

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(12);
        grid.setPadding(new Insets(15));

        double labelWidth = 90;
        Label[] labels = {
                new Label("Name"),
                new Label("Owner"),
                new Label("Year"),
                new Label("Priority"),
                new Label("Completion"),
                new Label("Location"),
                new Label("Description"),
                new Label("Tasks"),
                new Label("Overdue")
        };
        for (Label label : labels) {
            label.setPrefWidth(labelWidth);
            label.setAlignment(Pos.CENTER_RIGHT);
        }

        grid.addRow(0, labels[0], nameCombo);
        grid.addRow(1, labels[1], ownerCombo);
        grid.addRow(2, labels[2], yearField);
        grid.addRow(3, labels[3], priorityCombo);
        grid.addRow(4, labels[4], completionCombo);
        grid.addRow(5, labels[5], locationCombo);
        grid.addRow(6, labels[6], descriptionCombo);
        grid.addRow(7, labels[7], hasTasksCombo);
        grid.addRow(8, labels[8], overdueCombo);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(380);

        ButtonType apply = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
        ButtonType clear = new ButtonType("Clear", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(apply, clear, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn == clear) {
                ownerFilter = "All";
                yearFilter = null;
                priorityFilter = null;
                completionFilter = CompletionFilter.ALL;
                nameFilter = EmptyFilter.ALL;
                locationFilter = EmptyFilter.ALL;
                descriptionFilter = EmptyFilter.ALL;
                hasTasksFilter = YesNoFilter.ALL;
                overdueFilter = YesNoFilter.ALL;
                applyListFilters();
                return;
            }
            if (btn != apply) return;

            nameFilter = switch (nameCombo.getValue()) {
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

            String compVal = completionCombo.getValue();
            completionFilter = switch (compVal) {
                case "Completed" -> CompletionFilter.COMPLETED;
                case "Not completed" -> CompletionFilter.NOT_COMPLETED;
                default -> CompletionFilter.ALL;
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

            hasTasksFilter = switch (hasTasksCombo.getValue()) {
                case "Has tasks" -> YesNoFilter.YES;
                case "No tasks" -> YesNoFilter.NO;
                default -> YesNoFilter.ALL;
            };

            overdueFilter = switch (overdueCombo.getValue()) {
                case "Overdue" -> YesNoFilter.YES;
                case "Not overdue" -> YesNoFilter.NO;
                default -> YesNoFilter.ALL;
            };

            applyListFilters();
        });
    }

    // -----------------------------
    // Columns selection (kept)
    // -----------------------------

    private List<Column<Helpers.ListEntry>> getAllListColumns() {
        return List.of(
                new ListReorderColumn(),
                new ListNameColumn(),
                dk.dtu.collumns.Priority.forLists(),
                Year.forLists(),
                Location.forLists(),
                Description.forLists(),
                new ListCompletionColumn(),
                new ListTasksCountColumn(),
                new ListOverdueColumn(),
                new ListOwnerColumn(),
                new ListDeleteColumn()
        );
    }

    private List<Column<Helpers.ListEntry>> getVisibleListColumns() {
        List<Column<Helpers.ListEntry>> all = getAllListColumns();
        List<String> desired = loadListColumnIds();

        Map<String, Column<Helpers.ListEntry>> byId = new HashMap<>();
        for (Column<Helpers.ListEntry> col : all) byId.put(col.id(), col);

        List<Column<Helpers.ListEntry>> visible = new ArrayList<>();
        for (String id : desired) {
            Column<Helpers.ListEntry> col = byId.get(id);
            if (col != null) visible.add(col);
        }
        if (visible.isEmpty()) visible.addAll(all);

        // Enforce pinned ordering
        visible.removeIf(c -> PINNED_REORDER_ID.equals(c.id()) || PINNED_NAME_ID.equals(c.id()) || PINNED_DELETE_ID.equals(c.id()));

        Column<Helpers.ListEntry> reorder = byId.get(PINNED_REORDER_ID);
        Column<Helpers.ListEntry> name = byId.get(PINNED_NAME_ID);
        Column<Helpers.ListEntry> delete = byId.get(PINNED_DELETE_ID);

        List<Column<Helpers.ListEntry>> result = new ArrayList<>();
        if (reorder != null) result.add(reorder);
        if (name != null) result.add(name);
        result.addAll(visible);
        if (delete != null) result.add(delete);

        return result;
    }

    private List<String> loadListColumnIds() {
        Preferences prefs = Preferences.userNodeForPackage(C_MainMenu.class);
        String json = prefs.get(PREF_LIST_COLUMNS_JSON, "");

        List<String> defaultIds = getAllListColumns().stream().map(Column::id).toList();
        if (json == null || json.isBlank()) return ensurePinnedColumnOrder(defaultIds);

        try {
            List<String> parsed = GSON.fromJson(json, STRING_LIST_TYPE);
            if (parsed == null || parsed.isEmpty()) return ensurePinnedColumnOrder(defaultIds);
            return ensurePinnedColumnOrder(parsed);
        } catch (Exception e) {
            return ensurePinnedColumnOrder(defaultIds);
        }
    }

    private void saveListColumnIds(List<String> ids) {
        Preferences prefs = Preferences.userNodeForPackage(C_MainMenu.class);
        prefs.put(PREF_LIST_COLUMNS_JSON, GSON.toJson(ensurePinnedColumnOrder(ids)));
    }

    private List<String> ensurePinnedColumnOrder(List<String> ids) {
        List<String> allowedIds = getAllListColumns().stream().map(Column::id).toList();
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
        cleaned.remove(PINNED_NAME_ID);
        cleaned.remove(PINNED_DELETE_ID);

        cleaned.add(0, PINNED_NAME_ID);
        cleaned.add(0, PINNED_REORDER_ID);
        cleaned.add(PINNED_DELETE_ID);

        return cleaned;
    }

    private void showListColumnDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Choose columns");
        dialog.setHeaderText("Select which columns to show in the list overview");

        List<Column<Helpers.ListEntry>> all = getAllListColumns();
        List<String> currentOrder = loadListColumnIds();
        Set<String> currentVisible = new LinkedHashSet<>(currentOrder);

        Map<String, Column<Helpers.ListEntry>> byId = new HashMap<>();
        for (Column<Helpers.ListEntry> col : all) byId.put(col.id(), col);

        class Choice {
            final Column<Helpers.ListEntry> col;
            boolean selected;

            Choice(Column<Helpers.ListEntry> col, boolean selected) {
                this.col = col;
                this.selected = selected;
            }
        }

        ListView<Choice> choicesView = new ListView<>();
        choicesView.setPrefHeight(260);

        List<String> optionalIds = new ArrayList<>();
        for (String id : currentOrder) {
            if (PINNED_REORDER_ID.equals(id) || PINNED_NAME_ID.equals(id) || PINNED_DELETE_ID.equals(id)) continue;
            if (byId.containsKey(id) && !optionalIds.contains(id)) optionalIds.add(id);
        }
        for (Column<Helpers.ListEntry> col : all) {
            if (PINNED_REORDER_ID.equals(col.id()) || PINNED_NAME_ID.equals(col.id()) || PINNED_DELETE_ID.equals(col.id())) continue;
            if (!optionalIds.contains(col.id())) optionalIds.add(col.id());
        }

        for (String id : optionalIds) {
            Column<Helpers.ListEntry> col = byId.get(id);
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

        Label pinnedNote = new Label("Pinned: reorder handle + list name (always visible) • Delete is always last");
        VBox content = new VBox(10, pinnedNote, choicesView);
        content.setPadding(new Insets(10));

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;

            List<String> selected = new ArrayList<>();
            selected.add(PINNED_REORDER_ID);
            selected.add(PINNED_NAME_ID);

            for (Choice c : choicesView.getItems()) {
                if (c.selected) selected.add(c.col.id());
            }

            selected.add(PINNED_DELETE_ID);

            saveListColumnIds(selected);
            navigator.reloadMainMenu();
        });
    }

    public void openColumnsDialog() {
        showListColumnDialog();
    }
}