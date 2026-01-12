package dk.dtu.scenes;

import dk.dtu.SceneNavigator;
import dk.dtu.TupleSpaces;
import dk.dtu.Methods;
import dk.dtu.Methods.TaskEntry;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class D_TodoListView {

	private static final String HOST = "127.0.0.1";
	private static final int PORT = 9001;
	private static final String BASE_URI = "tcp://" + HOST + ":" + PORT + "/";

	private static final String TASKS_URI = BASE_URI + TupleSpaces.TASKS + "?keep";
	private static final String REQUESTS_URI = BASE_URI + TupleSpaces.REQUESTS + "?keep";
	private static final String RESPONSES_URI = BASE_URI + TupleSpaces.RESPONSES + "?keep";

	private final SceneNavigator navigator;
	private final String listId;
	private final String listName;

	public D_TodoListView(SceneNavigator navigator, String listId, String listName) {
		this.navigator = navigator;
		this.listId = listId;
		this.listName = listName;
	}

	public Scene createScene() {
		Label title = new Label("Todo List");
		title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

		Button mainMenuButton = new Button("Main menu");
		mainMenuButton.setOnAction(e -> navigator.showMainMenu());

		Label selected = new Label("Selected: " + listId + " - " + listName);
		Label statusLabel = new Label("");

		ListView<TaskEntry> tasksView = new ListView<>();
		tasksView.setPrefHeight(200);

		TextField newTaskField = new TextField();
		newTaskField.setPromptText("New task description");
		Button addTaskButton = new Button("Add Task");

		Button refreshButton = new Button("Refresh");
		refreshButton.setOnAction(e -> {
			Methods.loadTasksForList(statusLabel, refreshButton, tasksView, TASKS_URI, listId);
		});
		Methods.loadTasksForList(statusLabel, refreshButton, tasksView, TASKS_URI, listId);
		
		Button markDoneButton = new Button("Mark Done");
		markDoneButton.setOnAction(e -> {
			TaskEntry selectedTask = tasksView.getSelectionModel().getSelectedItem();
			if (selectedTask != null) {
				Methods.changeTaskStatus(statusLabel, markDoneButton, TASKS_URI, REQUESTS_URI, RESPONSES_URI, refreshButton, tasksView, listId, selectedTask.id, "DONE");
			} else {
				statusLabel.setText("No task selected to mark as done.");
			}
		});

		addTaskButton.setOnAction(e -> {
            String titleText = newTaskField.getText();
			Methods.addTaskToList(
				statusLabel,
				addTaskButton,
				TASKS_URI,
				REQUESTS_URI,
				RESPONSES_URI,
				refreshButton,
				tasksView,
				listId,
				titleText,
				""
			);			
        });

		HBox addBox = new HBox(8, newTaskField, addTaskButton);
		addBox.setAlignment(Pos.CENTER);

		HBox ops = new HBox(8, refreshButton, markDoneButton);
		ops.setAlignment(Pos.CENTER);

		VBox root = new VBox(12, title, mainMenuButton, selected, statusLabel, tasksView, addBox, ops);
		root.setAlignment(Pos.CENTER);
		root.setStyle("-fx-padding: 24;");

		return new Scene(root, 520, 360);
	}
}
