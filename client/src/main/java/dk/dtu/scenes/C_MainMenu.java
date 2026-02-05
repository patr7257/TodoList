package dk.dtu.scenes;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dk.dtu.SceneNavigator;
import dk.dtu.collumns.*;
import dk.dtu.methods.Helpers;
import dk.dtu.methods.Lists;
import dk.dtu.methods.Users;
import dk.dtu.shared.Config;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;

public class C_MainMenu {

    private static final double COLUMN_GAP = 15;

    private enum EmptyFilter { ALL, EMPTY, NOT_EMPTY }
    private enum YesNoFilter { ALL, YES, NO }

    private final SceneNavigator navigator;
    private final String loginMessage;

    private final ListView<Helpers.ListEntry> listsView = new ListView<>();

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

    private enum CompletionFilter { ALL, COMPLETED, NOT_COMPLETED }

    private static boolean matchesEmptyFilter(String value, EmptyFilter filter) {
        if (filter == null || filter == EmptyFilter.ALL) return true;
        boolean empty = value == null || value.isBlank();
        return switch (filter) {
            case EMPTY -> empty;
            case NOT_EMPTY -> !empty;
            default -> true;
        };
    }

    private static final Gson GSON = new Gson();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {}.getType();
    private static final String PREF_LIST_COLUMNS_JSON = "mainMenu.listColumnsJson";

    private static final String PINNED_REORDER_ID = "reorder";
    private static final String PINNED_NAME_ID = "name";
    private static final String PINNED_DELETE_ID = "delete";

    public C_MainMenu(SceneNavigator navigator) {
        this(navigator, null);
    }

    public C_MainMenu(SceneNavigator navigator, String loginMessage) {
        this.navigator = navigator;
        this.loginMessage = loginMessage;
    }

    public Scene createScene() {
        Label title = new Label("Available todo lists");
        title.getStyleClass().add("mainmenu-title");

        Label userLabel = new Label("Logged in as: " + navigator.getCurrentUser());

        Label tempMessageLabel = new Label();
        if (loginMessage != null && !loginMessage.isBlank()) {
            tempMessageLabel.setText(loginMessage);
            tempMessageLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");

            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
                Platform.runLater(() -> tempMessageLabel.setText(""));
            }, "login-message-timer").start();
        }

        List<Column<Helpers.ListEntry>> visibleColumns = getVisibleListColumns();
        SortState<Helpers.ListEntry> sortState = new SortState<>();

        double tableWidth = 0;
        for (Column<Helpers.ListEntry> col : visibleColumns) {
            tableWidth += col.prefWidth();
        }
        tableWidth += COLUMN_GAP * Math.max(0, visibleColumns.size() - 1);

        List<javafx.scene.Node> headerNodes = new ArrayList<>();
        List<Label> headerLabels = new ArrayList<>();
        for (Column<Helpers.ListEntry> col : visibleColumns) {
            javafx.scene.Node headerNode = col.createHeader(new ColumnHeaderContext<>(
                    listsView,
                    requested -> sortState.requestSort(requested, listsView, headerLabels)
            ));

            if (PINNED_NAME_ID.equals(col.id())) {
                HBox.setHgrow(headerNode, javafx.scene.layout.Priority.ALWAYS);
                if (headerNode instanceof Region r) {
                    r.setMaxWidth(Double.MAX_VALUE);
                }
            }
            if (headerNode instanceof Label l) {
                l.setUserData(col);
                headerLabels.add(l);
            }
            headerNodes.add(headerNode);
        }

        HBox header = new HBox(COLUMN_GAP, headerNodes.toArray(javafx.scene.Node[]::new));
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("list-header");
        header.setMinWidth(tableWidth);
        header.setPrefWidth(tableWidth);
        header.setMaxWidth(Double.MAX_VALUE);

        // When a list item is clicked, open that list
        listsView.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY|| e.getClickCount() != 2) {
                return;
            }
            Helpers.ListEntry selected = listsView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            navigator.showTodoList(selected.id, selected.name);
        });

        // Let the table be as wide as needed; horizontal scrolling is handled by a ScrollPane.
        listsView.setPrefWidth(tableWidth);
        listsView.setMinWidth(tableWidth);
        listsView.setMaxWidth(Double.MAX_VALUE);
        listsView.setPrefHeight(400);

        Runnable refreshLists = this::reloadLists;

        // Custom cells: built from visible columns
        listsView.setCellFactory(lv -> new ListCell<>() {

            private final HBox row;
            private final List<ColumnCell<Helpers.ListEntry>> cells;
            private final ContextMenu contextMenu = new ContextMenu();
            private javafx.scene.Node reorderHandle;

            {
                cells = new ArrayList<>();
                List<javafx.scene.Node> cellNodes = new ArrayList<>();
                for (Column<Helpers.ListEntry> col : visibleColumns) {
                    ColumnCell<Helpers.ListEntry> cellPart = col.createCell(new ColumnCellContext<>(this, refreshLists));
                    cells.add(cellPart);
                    javafx.scene.Node n = cellPart.node();

                    StackPane wrapper = new StackPane(n);
                    wrapper.setAlignment(Pos.CENTER);
                    wrapper.setMinWidth(col.prefWidth());
                    wrapper.setPrefWidth(col.prefWidth());
                    wrapper.setMaxWidth(col.prefWidth());

                    cellNodes.add(wrapper);
                    if (PINNED_REORDER_ID.equals(col.id())) {
                        reorderHandle = n;
                    }

                    if (PINNED_NAME_ID.equals(col.id())) {
                        HBox.setHgrow(wrapper, javafx.scene.layout.Priority.ALWAYS);
                        wrapper.setMaxWidth(Double.MAX_VALUE);
                        if (n instanceof Region r) {
                            r.setMaxWidth(Double.MAX_VALUE);
                        }
                    }
                }
                row = new HBox(COLUMN_GAP, cellNodes.toArray(javafx.scene.Node[]::new));
                row.setAlignment(Pos.CENTER_LEFT);

                // Drag start only from the reorder handle icon
                if (reorderHandle != null) {
                    reorderHandle.setOnDragDetected(evt -> {
                        Helpers.ListEntry item = getItem();
                        if (item == null) return;

                        Dragboard db = reorderHandle.startDragAndDrop(TransferMode.MOVE);
                        ClipboardContent content = new ClipboardContent();
                        content.putString(item.id);
                        db.setContent(content);

                        try {
                            db.setDragView(row.snapshot(null, null));
                        } catch (Exception ignored) {
                        }

                        setOpacity(0.4);
                        evt.consume();
                    });
                }

                setOnDragDone(evt -> setOpacity(1.0));

                // Accept drop anywhere on the row
                setOnDragOver(evt -> {
                    Dragboard db = evt.getDragboard();
                    if (db.hasString()) {
                        evt.acceptTransferModes(TransferMode.MOVE);
                    }
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

                    String draggedId = db.getString();
                    ListView<Helpers.ListEntry> view = getListView();
                    if (view == null) {
                        evt.setDropCompleted(false);
                        evt.consume();
                        return;
                    }

                    int draggedIdx = -1;
                    for (int i = 0; i < view.getItems().size(); i++) {
                        Helpers.ListEntry e = view.getItems().get(i);
                        if (e != null && draggedId.equals(e.id)) {
                            draggedIdx = i;
                            break;
                        }
                    }
                    if (draggedIdx < 0) {
                        evt.setDropCompleted(false);
                        evt.consume();
                        return;
                    }

                    int targetIdx = isEmpty() ? view.getItems().size() : Math.max(0, getIndex());
                    Helpers.ListEntry dragged = view.getItems().remove(draggedIdx);
                    if (targetIdx > view.getItems().size()) targetIdx = view.getItems().size();
                    if (targetIdx > draggedIdx) targetIdx--; // account for removal
                    view.getItems().add(targetIdx, dragged);

                    // keep selection on moved item
                    view.getSelectionModel().select(dragged);

                    evt.setDropCompleted(true);
                    evt.consume();

                    row.setStyle("");

                    // Persist order
                    List<String> orderedIds = view.getItems().stream().map(e -> e.id).toList();
                    new Thread(() -> {
                        try {
                            Lists.setListOrderBulk(Config.getRequestsUri(), Config.getResponsesUri(), orderedIds);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }, "persist-list-order").start();
                });

                MenuItem renameItem = new MenuItem("Rename list");
                renameItem.setOnAction(evt -> {
                    Helpers.ListEntry item = getItem();
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
                                Platform.runLater(refreshLists);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }, "rename-list").start();
                    });
                });
                contextMenu.getItems().add(renameItem);
            }

            @Override
            protected void updateItem(Helpers.ListEntry item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                    setContextMenu(null);
                    return;
                }

                for (ColumnCell<Helpers.ListEntry> c : cells) {
                    c.update(item);
                }

                setGraphic(row);
                setContextMenu(contextMenu);
            }
        });

        // "+ Create new list" link under the list
        Hyperlink createLink = new Hyperlink("+  Create new list");
        createLink.getStyleClass().add("create-link");
        createLink.setOnAction(e -> showCreateListDialog());

        VBox titleSection = new VBox(5, title, userLabel, tempMessageLabel);
        titleSection.setAlignment(Pos.TOP_CENTER);
        
        VBox root = new VBox(
                titleSection,
                new Label(""), // gap spacer
            createScrollableTable(header, listsView),
                createLink);
        root.setSpacing(10);
        root.setPadding(new Insets(24));
        root.setAlignment(Pos.TOP_CENTER);
        root.setFillWidth(true);           // <- important: let children stretch horizontally
        root.getStyleClass().add("mainmenu-root");

        // Initial load of lists
        refreshLists.run();

        return new Scene(root, 900, 600);
    }

    private ScrollPane createScrollableTable(HBox header, ListView<Helpers.ListEntry> listsView) {
        VBox table = new VBox(0, header, listsView);
        VBox.setVgrow(listsView, javafx.scene.layout.Priority.ALWAYS);
        table.minWidthProperty().bind(header.minWidthProperty());

        ScrollPane scroll = new ScrollPane(table);
        scroll.setPannable(true);
        // Fill remaining space when table is narrow; still allows scrolling when wider than the viewport.
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(false);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        // Vertical scrolling is handled by the ListView itself.
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    private void showCreateListDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create New To Do List");
        dialog.setHeaderText("Enter name for the new list:");
        dialog.setContentText("Name:");

        dialog.showAndWait().ifPresent(name -> {
            if (name == null || name.isBlank())
                return;

            new Thread(() -> {
                try {
                    Lists.createTodoList(
                            Config.getRequestsUri(),
                            Config.getResponsesUri(),
                            name,
                            navigator.getCurrentUser());
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
        Helpers.ListEntry selected = listsView.getSelectionModel().getSelectedItem();
        final String previouslySelectedId = (selected != null) ? selected.id : null;

        Lists.loadTodoLists(Config.getTodoListsUri(), entries -> {
            allLists = (entries != null) ? entries : List.of();
            applyListFilters();

            if (previouslySelectedId != null) {
                for (Helpers.ListEntry e : listsView.getItems()) {
                    if (e != null && previouslySelectedId.equals(e.id)) {
                        listsView.getSelectionModel().select(e);
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

            if (!matchesEmptyFilter(e.name, nameFilter)) {
                continue;
            }

            if (ownerFilter != null && !"All".equals(ownerFilter)) {
                if (e.owner == null || !ownerFilter.equals(e.owner)) {
                    continue;
                }
            }

            if (yearFilter != null && e.year != yearFilter) {
                continue;
            }

            if (priorityFilter != null && e.priority != priorityFilter) {
                continue;
            }

            if (completionFilter == CompletionFilter.COMPLETED && e.completionPercentage < 100) {
                continue;
            }
            if (completionFilter == CompletionFilter.NOT_COMPLETED && e.completionPercentage >= 100) {
                continue;
            }

            if (!matchesEmptyFilter(e.location, locationFilter)) {
                continue;
            }

            if (!matchesEmptyFilter(e.description, descriptionFilter)) {
                continue;
            }

            if (hasTasksFilter == YesNoFilter.YES && e.taskCount <= 0) {
                continue;
            }
            if (hasTasksFilter == YesNoFilter.NO && e.taskCount > 0) {
                continue;
            }

            boolean isOverdue = e.overdueTaskCount > 0;
            if (overdueFilter == YesNoFilter.YES && !isOverdue) {
                continue;
            }
            if (overdueFilter == YesNoFilter.NO && isOverdue) {
                continue;
            }

            filtered.add(e);
        }

        listsView.getItems().setAll(filtered);
    }

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
        for (int p = 1; p <= 10; p++) {
            priorityCombo.getItems().add(Integer.toString(p));
        }
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

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("Name"), nameCombo);
        grid.addRow(1, new Label("Owner"), ownerCombo);
        grid.addRow(2, new Label("Year"), yearField);
        grid.addRow(3, new Label("Priority"), priorityCombo);
        grid.addRow(4, new Label("Completion"), completionCombo);
        grid.addRow(5, new Label("Location"), locationCombo);
        grid.addRow(6, new Label("Description"), descriptionCombo);
        grid.addRow(7, new Label("Tasks"), hasTasksCombo);
        grid.addRow(8, new Label("Overdue"), overdueCombo);

        dialog.getDialogPane().setContent(grid);
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
            ownerFilter = (ownerVal == null || ownerVal.isBlank()) ? "All" : ownerVal;

            String yearText = yearField.getText();
            if (yearText == null || yearText.isBlank()) {
                yearFilter = null;
            } else {
                try {
                    yearFilter = Integer.parseInt(yearText.trim());
                } catch (NumberFormatException ex) {
                    yearFilter = null;
                }
            }

            String prioVal = priorityCombo.getValue();
            if (prioVal == null || prioVal.isBlank() || "All".equals(prioVal)) {
                priorityFilter = null;
            } else {
                try {
                    priorityFilter = Integer.parseInt(prioVal);
                } catch (NumberFormatException ex) {
                    priorityFilter = null;
                }
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
    
    private List<Column<Helpers.ListEntry>> getAllListColumns() {
        return List.of(
                new ListReorderColumn(),
                new ListNameColumn(),
                Priority.forLists(),
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

        java.util.Map<String, Column<Helpers.ListEntry>> byId = new java.util.HashMap<>();
        for (Column<Helpers.ListEntry> col : all) {
            byId.put(col.id(), col);
        }

        List<Column<Helpers.ListEntry>> visible = new ArrayList<>();
        for (String id : desired) {
            Column<Helpers.ListEntry> col = byId.get(id);
            if (col != null) {
                visible.add(col);
            }
        }

        if (visible.isEmpty()) {
            visible.addAll(all);
        }

        // Enforce pinned ordering (reorder + name always first; delete always last)
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
        if (json == null || json.isBlank()) {
            return ensurePinnedColumnOrder(defaultIds);
        }

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

        // Delete is always the last column.
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

        java.util.Map<String, Column<Helpers.ListEntry>> byId = new java.util.HashMap<>();
        for (Column<Helpers.ListEntry> col : all) {
            byId.put(col.id(), col);
        }

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

        // Optional columns: everything except pinned reorder + name
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

                    try {
                        db.setDragView(snapshot(null, null));
                    } catch (Exception ignored) {
                    }
                    evt.consume();
                });

                setOnDragOver(evt -> {
                    Dragboard db = evt.getDragboard();
                    if (db.hasString()) {
                        evt.acceptTransferModes(TransferMode.MOVE);
                    }
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
                    try {
                        from = Integer.parseInt(db.getString());
                    } catch (Exception e) {
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
                if (c.selected) {
                    selected.add(c.col.id());
                }
            }

            // Always include delete as the last column.
            selected.add(PINNED_DELETE_ID);

            saveListColumnIds(selected);
            navigator.reloadMainMenu();
        });
    }

    public void openColumnsDialog() {
        showListColumnDialog();
    }
}