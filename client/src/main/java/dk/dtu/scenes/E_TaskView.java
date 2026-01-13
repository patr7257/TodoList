package dk.dtu.scenes;

import dk.dtu.Config;
import dk.dtu.Methods;
import dk.dtu.Methods.TaskEntry;
import dk.dtu.SceneNavigator;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
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

    /** Scene used by SceneNavigator.showTaskView(...) */
    public Scene createScene() {
        Label title = new Label("Task View");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Button mainMenuButton = new Button("Main menu");
        mainMenuButton.setOnAction(e -> navigator.showMainMenu());

        Label selected = new Label("Selected: " + listId + " - " + listName);
        Label statusLabel = new Label("");

        VBox tasksPane = createTasksPane(statusLabel, listId, navigator.getCurrentUser());

        VBox root = new VBox(12, title, mainMenuButton, selected, statusLabel, tasksPane);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 24;");

        return new Scene(root, 520, 360);
    }

    /** Reusable pane (also used by D_TodoListView) */
    public static VBox createTasksPane(Label statusLabel, String listId, String currentUser) {
        ListView<TaskEntry> tasksView = new ListView<>();
        tasksView.setPrefHeight(200);

        TextField newTaskField = new TextField();
        newTaskField.setPromptText("New task description");

        Button addTaskButton = new Button("Add Task");
        Button refreshButton = new Button("Refresh");
        Button markDoneButton = new Button("Mark Done");

        refreshButton.setOnAction(e -> Methods.loadTasksForList(
                statusLabel, refreshButton, tasksView, Config.TASKS_URI, listId));

        addTaskButton.setOnAction(e -> Methods.addTaskToList(
                statusLabel,
                addTaskButton,
                Config.TASKS_URI,
                Config.REQUESTS_URI,
                Config.RESPONSES_URI,
                refreshButton,
                tasksView,
                listId,
                newTaskField.getText(),
                currentUser == null ? "" : currentUser
        ));

        markDoneButton.setOnAction(e -> {
            TaskEntry selectedTask = tasksView.getSelectionModel().getSelectedItem();
            if (selectedTask == null) {
                Methods.setStatus(statusLabel, "No task selected to mark as done.");
                return;
            }
            Methods.changeTaskStatus(
                    statusLabel,
                    markDoneButton,
                    Config.TASKS_URI,
                    Config.REQUESTS_URI,
                    Config.RESPONSES_URI,
                    refreshButton,
                    tasksView,
                    listId,
                    selectedTask.id,
                    "DONE"
            );
        });

        // Initial load
        Methods.loadTasksForList(statusLabel, refreshButton, tasksView, Config.TASKS_URI, listId);

        HBox addBox = new HBox(8, newTaskField, addTaskButton);
        addBox.setAlignment(Pos.CENTER);

        HBox ops = new HBox(8, refreshButton, markDoneButton);
        ops.setAlignment(Pos.CENTER);

        VBox root = new VBox(12, tasksView, addBox, ops);
        root.setAlignment(Pos.CENTER);
        return root;
    }
}
