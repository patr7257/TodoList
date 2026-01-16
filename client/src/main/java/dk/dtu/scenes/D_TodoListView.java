package dk.dtu.scenes;

import dk.dtu.SceneNavigator;
import dk.dtu.methods.Helpers;
import dk.dtu.methods.Tasks;
import dk.dtu.shared.Config;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

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

        // Title + small description
        Label title = new Label("Tasks in: " + listName);
        title.getStyleClass().add("todolist-title");

        Label subtitle = new Label("Overview of all tasks in this list");
        subtitle.getStyleClass().add("todolist-subtitle");

        Label info = new Label("List ID: " + listId);
        info.getStyleClass().add("todolist-meta");

        // Header row: Task | Status | Owner | Delete  (same style idea as main menu)
        Label taskHeader = new Label("Task");
        taskHeader.setPrefWidth(230);

        Label statusHeader = new Label("Status");
        statusHeader.setPrefWidth(150);

        Label ownerHeader = new Label("Owner");
        ownerHeader.setPrefWidth(150);

        Label deleteHeader = new Label("Delete");
        deleteHeader.setPrefWidth(80);

        HBox header = new HBox(30, taskHeader, statusHeader, ownerHeader, deleteHeader);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setMaxWidth(700);
        header.getStyleClass().add("tasks-header"); // style similar to list-header

        // Task list
        tasksView.setPrefWidth(700);
        tasksView.setMaxWidth(700);
        tasksView.setPrefHeight(260);
        tasksView.getStyleClass().add("todolist-tasks");

        // Custom rows: Task | Status | Owner | Delete button
        tasksView.setCellFactory(lv -> new ListCell<>() {

            private final Label taskLabel = new Label();
            private final Label statusLabel = new Label();
            private final Label ownerLabel = new Label();
            private final Button deleteButton = new Button("✖");

            private final HBox row = new HBox(taskLabel, statusLabel, ownerLabel, deleteButton);

            {
                // Match header widths
                taskLabel.setPrefWidth(230);
                statusLabel.setPrefWidth(150);
                ownerLabel.setPrefWidth(110);
                deleteButton.setPrefWidth(70);

                row.setSpacing(30);
                row.setAlignment(Pos.CENTER_LEFT);

                taskLabel.getStyleClass().add("task-col-name");
                statusLabel.getStyleClass().add("task-col-status");
                ownerLabel.getStyleClass().add("task-col-owner");
                deleteButton.getStyleClass().add("task-col-delete-button");

                // Delete should not trigger row selection
                deleteButton.addEventFilter(MouseEvent.MOUSE_PRESSED, evt -> {
                    evt.consume();
                    Helpers.TaskEntry item = getItem();
                    if (item == null) {
                        return;
                    }

                    System.out.println("Delete task: " + item.id);

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
                            ex.printStackTrace();
                            Platform.runLater(() -> deleteButton.setDisable(false));
                        }
                    }, "delete-task").start();
                });
            }

            @Override
            protected void updateItem(Helpers.TaskEntry item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                // Task name / description
                // TODO: replace item.toString() with the real field, e.g. item.description
                taskLabel.setText(item.toString());

                // TODO: when TaskEntry exposes status/owner, wire real values here
                statusLabel.setText("–");
                ownerLabel.setText("–");

                setGraphic(row);
            }
        });

        // Load tasks for this list
        Tasks.loadTasksForList(tasksView, Config.getTasksUri(), listId);

        Label hint = new Label("Open the task manager to assign tasks or change their status.");
        hint.getStyleClass().add("todolist-hint");

        // Buttons under the table
        Button openManagerButton = new Button("Open task manager");
        openManagerButton.getStyleClass().add("primary-button");
        openManagerButton.setOnAction(e -> navigator.showTaskView(listId, listName));

        Button backButton = new Button("Back to lists");
        backButton.getStyleClass().add("nav-button");
        backButton.setOnAction(e -> navigator.showMainMenu());

        HBox buttonsBox = new HBox(20, openManagerButton, backButton);
        buttonsBox.setAlignment(Pos.CENTER);

        // Layout container (similar spacing / padding / alignment as main menu)
        VBox root = new VBox(
                20,
                title,
                subtitle,
                info,
                header,
                tasksView,
                hint,
                buttonsBox
        );
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
}