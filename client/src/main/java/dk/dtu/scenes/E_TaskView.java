package dk.dtu.scenes;

import dk.dtu.Config;
import dk.dtu.Methods;
import dk.dtu.Methods.TaskEntry;
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

        Label statusLabel = new Label("");

        // Task ListView
        ListView<TaskEntry> tasksView = new ListView<>();
        tasksView.setPrefHeight(200);

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> Methods.loadTasksForList(
            statusLabel, refreshButton, tasksView, Config.TASKS_URI, listId));

        // Add task section
        TextField newTaskField = new TextField();
        newTaskField.setPromptText("New task description");
        
        Button addTaskButton = new Button("Add Task");
        addTaskButton.setOnAction(e -> {
            Methods.addTaskToList(
                statusLabel,
                addTaskButton,
                Config.TASKS_URI,
                Config.REQUESTS_URI,
                Config.RESPONSES_URI,
                refreshButton,
                tasksView,
                listId,
                newTaskField.getText(),
                navigator.getCurrentUser()
            );
            newTaskField.clear();
        });

        // Change status section
        ComboBox<String> statusComboBox = new ComboBox<>();
        statusComboBox.getItems().addAll("PENDING", "IN_PROGRESS", "DONE");
        statusComboBox.setPromptText("Select Status");
        statusComboBox.setValue("DONE");
        
        Button changeStatusButton = new Button("Change Status");
        changeStatusButton.setOnAction(e -> {
            TaskEntry selectedTask = tasksView.getSelectionModel().getSelectedItem();
            if (selectedTask == null) {
                Methods.setStatus(statusLabel, "No task selected");
                return;
            }
            String newStatus = statusComboBox.getValue();
            if (newStatus == null) {
                Methods.setStatus(statusLabel, "Select a status");
                return;
            }
            Methods.changeTaskStatus(
                statusLabel,
                changeStatusButton,
                Config.TASKS_URI,
                Config.REQUESTS_URI,
                Config.RESPONSES_URI,
                refreshButton,
                tasksView,
                listId,
                selectedTask.id,
                newStatus
            );
        });

        // Assign task section
        TextField assignField = new TextField();
        assignField.setPromptText("Assign to user");
        
        Button assignButton = new Button("Assign");
        assignButton.setOnAction(e -> {
            TaskEntry selectedTask = tasksView.getSelectionModel().getSelectedItem();
            if (selectedTask == null) {
                Methods.setStatus(statusLabel, "No task selected");
                return;
            }
            Methods.assignTaskToList(
                statusLabel, 
                assignButton, 
                Config.REQUESTS_URI, 
                Config.RESPONSES_URI,
                refreshButton, 
                tasksView, 
                listId, 
                selectedTask.id, 
                assignField.getText());
            assignField.clear();
        });

        Button deleteButton = new Button("Delete");
        deleteButton.setOnAction(e -> {
            TaskEntry selectedTask = tasksView.getSelectionModel().getSelectedItem();
            if (selectedTask == null) {
                Methods.setStatus(statusLabel, "No task selected");
                return;
            }

            Methods.deleteTaskFromList(
                statusLabel,
                deleteButton,
                Config.REQUESTS_URI,
                Config.RESPONSES_URI,
                refreshButton,
                tasksView,
                listId,
                selectedTask.id
            );
        });

        // Initial load
        Methods.loadTasksForList(statusLabel, refreshButton, tasksView, Config.TASKS_URI, listId);

        // Layout
        HBox navButtons = new HBox(8, backButton, mainMenuButton);
        navButtons.setAlignment(Pos.CENTER);

        HBox addBox = new HBox(8, newTaskField, addTaskButton);
        addBox.setAlignment(Pos.CENTER);

        HBox statusBox = new HBox(8, statusComboBox, changeStatusButton);
        statusBox.setAlignment(Pos.CENTER);

        HBox assignBox = new HBox(8, assignField, assignButton);
        assignBox.setAlignment(Pos.CENTER);

        HBox deleteBox = new HBox(8, deleteButton);
        deleteBox.setAlignment(Pos.CENTER);

        Label hint = new Label("(Select a task from the list above)");
        hint.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

        VBox root = new VBox(12, 
            title, 
            navButtons,
            statusLabel,
            tasksView,
            refreshButton,
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
}
