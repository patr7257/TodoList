package dk.dtu.scenes;

import dk.dtu.SceneNavigator;
import dk.dtu.methods.Helpers;
import dk.dtu.methods.Tasks;
import dk.dtu.methods.Users;
import dk.dtu.shared.Config;
import dk.dtu.shared.TaskStatus;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;

public class D_TodoListView {

    private final SceneNavigator navigator;
    private final String listId;
    private final String listName;

    private final ListView<Helpers.TaskEntry> tasksView = new ListView<>();

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

        // Sorting state
        final boolean[] sortAscending = {true}; // Track sort direction

        // Declare all headers first
        Label taskHeader = new Label("Task ▲▼");
        taskHeader.setPrefWidth(250);
        taskHeader.setAlignment(Pos.CENTER);
        taskHeader.setStyle("-fx-font-weight: bold; -fx-cursor: hand;");

        Label statusHeader = new Label("Status ▲▼");
        statusHeader.setPrefWidth(145);
        statusHeader.setAlignment(Pos.CENTER);
        statusHeader.setStyle("-fx-font-weight: bold; -fx-cursor: hand;");

        Label dueHeader = new Label("Due date ▲▼");
        dueHeader.setPrefWidth(145);
        dueHeader.setAlignment(Pos.CENTER);
        dueHeader.setStyle("-fx-font-weight: bold; -fx-cursor: hand;");

        Label ownerHeader = new Label("Owner ▲▼");
        ownerHeader.setPrefWidth(145);
        ownerHeader.setAlignment(Pos.CENTER);
        ownerHeader.setStyle("-fx-font-weight: bold; -fx-cursor: hand;");
        
        // Now add click handlers that reference all headers
        taskHeader.setOnMouseClicked(e -> {
            sortAscending[0] = !sortAscending[0];
            javafx.collections.FXCollections.sort(tasksView.getItems(), (a, b) -> {
                int result = a.title.compareToIgnoreCase(b.title);
                return sortAscending[0] ? result : -result;
            });
            updateSortIndicators(taskHeader, statusHeader, dueHeader, ownerHeader);
        });
        
        statusHeader.setOnMouseClicked(e -> {
            sortAscending[0] = !sortAscending[0];
            javafx.collections.FXCollections.sort(tasksView.getItems(), (a, b) -> {
                String statusA = a.status != null ? a.status : "";
                String statusB = b.status != null ? b.status : "";
                int result = statusA.compareToIgnoreCase(statusB);
                return sortAscending[0] ? result : -result;
            });
            updateSortIndicators(statusHeader, taskHeader, dueHeader, ownerHeader);
        });
        
        dueHeader.setOnMouseClicked(e -> {
            sortAscending[0] = !sortAscending[0];
            javafx.collections.FXCollections.sort(tasksView.getItems(), (a, b) -> {
                String dateA = a.dueDate != null && !a.dueDate.isEmpty() ? a.dueDate : "9999-12-31"; // Empty dates last
                String dateB = b.dueDate != null && !b.dueDate.isEmpty() ? b.dueDate : "9999-12-31";
                int result = dateA.compareTo(dateB);
                return sortAscending[0] ? result : -result;
            });
            updateSortIndicators(dueHeader, taskHeader, statusHeader, ownerHeader);
        });
        
        ownerHeader.setOnMouseClicked(e -> {
            sortAscending[0] = !sortAscending[0];
            javafx.collections.FXCollections.sort(tasksView.getItems(), (a, b) -> {
                String ownerA = a.owner != null ? a.owner : "";
                String ownerB = b.owner != null ? b.owner : "";
                int result = ownerA.compareToIgnoreCase(ownerB);
                return sortAscending[0] ? result : -result;
            });
            updateSortIndicators(ownerHeader, taskHeader, statusHeader, dueHeader);
        });

        Label deleteHeader = new Label("");
        deleteHeader.setPrefWidth(50);

        HBox header = new HBox(20, taskHeader, statusHeader, dueHeader, ownerHeader, deleteHeader);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setMaxWidth(850);
        header.getStyleClass().add("tasks-header");

        tasksView.setPrefWidth(850);
        tasksView.setMaxWidth(850);
        tasksView.setPrefHeight(400);
        tasksView.getStyleClass().add("todolist-tasks");
        tasksView.setCellFactory(lv -> new ListCell<>() {

            private final Label taskLabel = new Label();
            private final ComboBox<TaskStatus> statusCombo = new ComboBox<>();
            private final DatePicker duePicker = new DatePicker();
            private final ComboBox<String> ownerCombo = new ComboBox<>();
            private final Button deleteButton = new Button();

            private final HBox row = new HBox(15, taskLabel, statusCombo, duePicker, ownerCombo, deleteButton);

            {
                taskLabel.setPrefWidth(250);
                taskLabel.setMinWidth(250);
                taskLabel.setMaxWidth(250);
                
                statusCombo.setPrefWidth(145);
                statusCombo.setMinWidth(145);
                statusCombo.setMaxWidth(145);
                statusCombo.getItems().addAll(TaskStatus.values());
                statusCombo.setPromptText("Status");
                
                // Apply CSS styling to status cells
                statusCombo.setCellFactory(lv -> createStatusCell());
                statusCombo.setButtonCell(createStatusCell());
                
                duePicker.setPrefWidth(145);
                duePicker.setMinWidth(145);
                duePicker.setMaxWidth(145);
                duePicker.setPromptText("Due date");
                
                ownerCombo.setPrefWidth(145);
                ownerCombo.setMinWidth(145);
                ownerCombo.setMaxWidth(145);
                ownerCombo.setPromptText("Owner");
                
                deleteButton.setPrefWidth(50);
                deleteButton.setMinWidth(50);
                deleteButton.setMaxWidth(50);
                
                ImageView deleteIcon = new ImageView(new Image(getClass().getResourceAsStream("/Icons/deleteicon.png")));
                deleteIcon.setFitWidth(28);
                deleteIcon.setFitHeight(28);
                deleteButton.setGraphic(deleteIcon);

                row.setAlignment(Pos.CENTER_LEFT);

                taskLabel.getStyleClass().add("task-col-name");
                statusCombo.getStyleClass().add("task-col-status");
                duePicker.getStyleClass().add("task-col-due");
                ownerCombo.getStyleClass().add("task-col-owner");
                deleteButton.getStyleClass().add("list-col-delete-button");

                // Load users once when cell is created
                Users.loadUsersIntoComboBox(ownerCombo, Config.getUsersUri());

                // Status change handler
                statusCombo.setOnAction(evt -> {
                    Helpers.TaskEntry item = getItem();
                    if (item == null) return;
                    
                    TaskStatus newStatus = statusCombo.getValue();
                    if (newStatus == null) return;
                    
                    // Don't trigger if it's the same as current
                    if (newStatus.name().equals(item.status)) return;

                    statusCombo.setDisable(true);
                    new Thread(() -> {
                        try {
                            Tasks.changeTaskStatus(
                                    Config.getRequestsUri(),
                                    Config.getResponsesUri(),
                                    item.listId,
                                    item.id,
                                    newStatus.name()
                            );
                            Platform.runLater(() -> {
                                statusCombo.setDisable(false);
                                Tasks.loadTasksForList(tasksView, Config.getTasksUri(), listId);
                            });
                        } catch (Exception ex) {
                            System.out.println("[CLIENT] ERROR changing task status:");
                            ex.printStackTrace();
                            Platform.runLater(() -> statusCombo.setDisable(false));
                        }
                    }, "change-task-status").start();
                });

                // Due date change handler
                duePicker.setOnAction(evt -> {
                    Helpers.TaskEntry item = getItem();
                    if (item == null) return;
                    
                    LocalDate newDate = duePicker.getValue();
                    if (newDate == null) return;
                    
                    String newDueDate = newDate.toString();
                    // Don't trigger if it's the same as current
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
                                Tasks.loadTasksForList(tasksView, Config.getTasksUri(), listId);
                            });
                        } catch (Exception ex) {
                            System.out.println("[CLIENT] ERROR changing due date:");
                            ex.printStackTrace();
                            Platform.runLater(() -> duePicker.setDisable(false));
                        }
                    }, "change-due-date").start();
                });

                // Owner change handler
                ownerCombo.setOnAction(evt -> {
                    Helpers.TaskEntry item = getItem();
                    if (item == null) return;
                    
                    String newOwner = ownerCombo.getValue();
                    if (newOwner == null || newOwner.isBlank()) return;
                    
                    // Don't trigger if it's the same as current
                    if (newOwner.equals(item.owner)) return;

                    ownerCombo.setDisable(true);
                    new Thread(() -> {
                        try {
                            Tasks.assignTask(
                                    Config.getRequestsUri(),
                                    Config.getResponsesUri(),
                                    item.listId,
                                    item.id,
                                    newOwner
                            );
                            Platform.runLater(() -> {
                                ownerCombo.setDisable(false);
                                Tasks.loadTasksForList(tasksView, Config.getTasksUri(), listId);
                            });
                        } catch (Exception ex) {
                            System.out.println("[CLIENT] ERROR assigning task:");
                            ex.printStackTrace();
                            Platform.runLater(() -> ownerCombo.setDisable(false));
                        }
                    }, "assign-task").start();
                });

                // Delete handler
                deleteButton.addEventFilter(MouseEvent.MOUSE_PRESSED, evt -> {
                    evt.consume(); // prevent row click selection

                    Helpers.TaskEntry item = getItem();
                    if (item == null) return;

                    // Show confirmation dialog
                    Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
                    confirmAlert.setTitle("Delete Task");
                    confirmAlert.setHeaderText("Are you sure you want to delete this task?");
                    confirmAlert.setContentText("Task: " + item.title + "\nThis action cannot be undone.");
                    
                    confirmAlert.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.OK) {
                            deleteButton.setDisable(true);

                            new Thread(() -> {
                                try {
                                    Tasks.deleteTask(
                                            Config.getRequestsUri(),
                                            Config.getResponsesUri(),
                                            item.id
                                    );

                                    Platform.runLater(() -> {
                                        deleteButton.setDisable(false);
                                        Tasks.loadTasksForList(tasksView, Config.getTasksUri(), listId);
                                    });

                                } catch (Exception ex) {
                                    System.out.println("[CLIENT] ERROR deleting task:");
                                    ex.printStackTrace();
                                    Platform.runLater(() -> deleteButton.setDisable(false));
                                }
                            }, "delete-task").start();
                        }
                    });
                });
            }

            @Override
            protected void updateItem(Helpers.TaskEntry item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                // Set task name
                taskLabel.setText(item.nameToString());
                taskLabel.setTooltip(new Tooltip(item.nameToString()));
                
                // Set status dropdown value
                try {
                    statusCombo.setValue(TaskStatus.valueOf(item.status));
                } catch (Exception e) {
                    statusCombo.setValue(null);
                }
                
                // Set due date value
                try {
                    if (item.dueDate != null && !item.dueDate.isBlank()) {
                        duePicker.setValue(LocalDate.parse(item.dueDate));
                    } else {
                        duePicker.setValue(null);
                    }
                } catch (Exception e) {
                    duePicker.setValue(null);
                }
                
                // Set owner dropdown value
                if (item.owner != null && !item.owner.isBlank()) {
                    ownerCombo.setValue(item.owner);
                } else {
                    ownerCombo.setValue(null);
                }

                setGraphic(row);
            }
        });

        // Load tasks initially
        Tasks.loadTasksForList(tasksView, Config.getTasksUri(), listId);

        // "+ Add new task" link under the table
        Hyperlink addTaskLink = new Hyperlink("+  Add new task");
        addTaskLink.getStyleClass().add("create-link");
        addTaskLink.setOnAction(e -> showCreateTaskDialog());

        Label hint = new Label("Use the dropdown menus to edit status, due date, and owner for each task.");
        hint.getStyleClass().add("todolist-hint");

        VBox titleSection = new VBox(5, title, info);
        titleSection.setAlignment(Pos.TOP_CENTER);
        
        VBox root = new VBox(
                titleSection,
                new Label(""),
                header,
                tasksView,
                addTaskLink,
                hint
        );
        root.setSpacing(10);
        root.setPadding(new Insets(24));
        root.setAlignment(Pos.TOP_CENTER);
        root.setFillWidth(true);
        root.getStyleClass().add("todolist-root");

        return new Scene(root, 900, 600);
    }
    // Refresh tasks view when notification received
    public void autoRefreshTasks() {
        Tasks.loadTasksForList(tasksView, Config.getTasksUri(), listId);
    }

    // Helper method to create status cells with CSS styling
    private ListCell<TaskStatus> createStatusCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(TaskStatus status, boolean empty) {
                super.updateItem(status, empty);
                getStyleClass().removeAll("status-NOT_STARTED", "status-IN_PROGRESS", "status-DELAYED", "status-NEED_HELP", "status-DONE");
                if (empty || status == null) {
                    setText(null);
                } else {
                    setText(status.name());
                    getStyleClass().add("status-" + status.name());
                }
            }
        };
    }

    private void showCreateTaskDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add New Task");
        dialog.setHeaderText("Enter a description for the new task:");
        dialog.setContentText("Task:");

        dialog.showAndWait().ifPresent(name -> {
            if (name == null || name.isBlank()) return;

            new Thread(() -> {
                try {
                    // owner is empty here, can be set later in task manager / edit dialog
                    Tasks.addTask(
                            Config.getRequestsUri(),
                            Config.getResponsesUri(),
                            listId,
                            name,
                            "",  
                            ""
                    );
                    Platform.runLater(() ->
                            Tasks.loadTasksForList(tasksView, Config.getTasksUri(), listId)
                    );
                } catch (Exception ex) {
                    System.out.println("[CLIENT] ERROR creating task:");
                    ex.printStackTrace();
                }
            }, "create-task").start();
        });
    }
    
    /**
     * Update sort indicators on column headers
     */
    private void updateSortIndicators(Label activeHeader, Label header2, Label header3, Label header4) {
        // Highlight active header
        activeHeader.setStyle("-fx-font-weight: bold; -fx-cursor: hand; -fx-text-fill: #007bff;");
        
        // Reset other headers
        header2.setStyle("-fx-font-weight: bold; -fx-cursor: hand;");
        header3.setStyle("-fx-font-weight: bold; -fx-cursor: hand;");
        header4.setStyle("-fx-font-weight: bold; -fx-cursor: hand;");
    }
}