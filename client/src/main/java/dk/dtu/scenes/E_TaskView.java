package dk.dtu.scenes;

import dk.dtu.shared.Config;
import dk.dtu.shared.TaskStatus;
import dk.dtu.methods.*;
import dk.dtu.SceneNavigator;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class E_TaskView {

    private final SceneNavigator navigator;
    private final String listId;
    private final String listName;
    
    // Store references for auto-refresh
    private ListView<Helpers.TaskEntry> tasksView;

    public E_TaskView(SceneNavigator navigator, String listId, String listName) {
        this.navigator = navigator;
        this.listId = listId;
        this.listName = listName;
    }

    public Scene createScene() {
        Label title = new Label("Task Manager: " + listName);
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Button backButton = new Button("← Back to List");
        backButton.setOnAction(e -> navigator.showTodoList(listId, listName));

        Button mainMenuButton = new Button("Main Menu");
        mainMenuButton.setOnAction(e -> navigator.showMainMenu());

        ComboBox<String> assigneeComboBox = new ComboBox<>();
        assigneeComboBox.setPromptText("Select assignee");

        Users.loadUsersIntoComboBox(assigneeComboBox, Config.getUsersUri());


        // Task ListView
        tasksView = new ListView<>();
        tasksView.setPrefHeight(200);

        // Add task section
        TextField newTaskField = new TextField();
        newTaskField.setPromptText("New task description");
        
        Button addTaskButton = new Button("Add Task");
        addTaskButton.setOnAction(e -> {
            String selectedAssignee = assigneeComboBox.getValue();
            if (selectedAssignee == null || selectedAssignee.isBlank()) {
                return;
            }
            addTaskButton.setDisable(true);
            new Thread(() -> {
                try {
                    Tasks.addTask(Config.getRequestsUri(), Config.getResponsesUri(), listId, newTaskField.getText(), selectedAssignee);
                    javafx.application.Platform.runLater(() -> {
                        addTaskButton.setDisable(false);
                        newTaskField.clear();
                    });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> addTaskButton.setDisable(false));
                    ex.printStackTrace();
                }
            }, "add-task").start();
        });

        // Change status section
        ComboBox<TaskStatus> statusComboBox = new ComboBox<>();
        statusComboBox.getItems().addAll(TaskStatus.values());
        statusComboBox.setPromptText("Select Status");
        statusComboBox.setValue(TaskStatus.DONE);
        
        Button changeStatusButton = new Button("Change Status");
        changeStatusButton.setOnAction(e -> {
            Helpers.TaskEntry selectedTask = tasksView.getSelectionModel().getSelectedItem();
            if (selectedTask == null) {
                return;
            }
            TaskStatus newStatus = statusComboBox.getValue();
            if (newStatus == null) {
                return;
            }
            changeStatusButton.setDisable(true);
            new Thread(() -> {
                try {
                    Tasks.changeTaskStatus(Config.getRequestsUri(), Config.getResponsesUri(), listId, selectedTask.id, newStatus.name());
                    javafx.application.Platform.runLater(() -> changeStatusButton.setDisable(false));
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> changeStatusButton.setDisable(false));
                    ex.printStackTrace();
                }
            }, "change-status").start();
        });

        // Assign task section
        Button assignButton = new Button("Assign");
        assignButton.setOnAction(e -> {
            Helpers.TaskEntry selectedTask = tasksView.getSelectionModel().getSelectedItem();
            if (selectedTask == null) {
                return;
            }

            String selectedAssignee = assigneeComboBox.getValue();
            if (selectedAssignee == null || selectedAssignee.isBlank()) {
                return;
            }

            assignButton.setDisable(true);
            new Thread(() -> {
                try {
                    Tasks.assignTask(Config.getRequestsUri(), Config.getResponsesUri(), listId, selectedTask.id, selectedAssignee);
                    javafx.application.Platform.runLater(() -> assignButton.setDisable(false));
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> assignButton.setDisable(false));
                    ex.printStackTrace();
                }
            }, "assign-task").start();
        });

        Button deleteButton = new Button("Delete");
        deleteButton.setOnAction(e -> {
            Helpers.TaskEntry selectedTask = tasksView.getSelectionModel().getSelectedItem();
            if (selectedTask == null) {
                return;
            }
            
            deleteButton.setDisable(true);
            new Thread(() -> {
                try {
                    Tasks.deleteTask(Config.getRequestsUri(), Config.getResponsesUri(), selectedTask.id);
                    javafx.application.Platform.runLater(() -> deleteButton.setDisable(false));
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> deleteButton.setDisable(false));
                    ex.printStackTrace();
                }
            }, "delete-task").start();
        });

        // Initial load
        Tasks.loadTasksForList(tasksView, Config.getTasksUri(), listId);
        // Layout
        HBox navButtons = new HBox(8, backButton, mainMenuButton);
        navButtons.setAlignment(Pos.CENTER);

        HBox addBox = new HBox(8, newTaskField, addTaskButton);
        addBox.setAlignment(Pos.CENTER);

        HBox statusBox = new HBox(8, statusComboBox, changeStatusButton);
        statusBox.setAlignment(Pos.CENTER);

        HBox assignBox = new HBox(8, assigneeComboBox, assignButton);
        assignBox.setAlignment(Pos.CENTER);


        HBox deleteBox = new HBox(8, deleteButton);
        deleteBox.setAlignment(Pos.CENTER);

        Label hint = new Label("(Select a task from the list above)");
        hint.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

        VBox root = new VBox(12, 
            title, 
            navButtons,
            tasksView,
            new Label("Add New Task:"),
            addBox,
            hint,
            new Label("Change Task Status:"),
            statusBox,
            new Label("Assign Task:"),
            assignBox,
            new Label("Delete task"),
            deleteBox);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 24;");

        return new Scene(root, 640, 600);
    }
    
    // Refresh tasks view
    public void autoRefreshTasks() {
        Tasks.loadTasksForList(tasksView, Config.getTasksUri(), listId);
    }
}
