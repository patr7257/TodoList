package dk.dtu.scenes;

import dk.dtu.SceneNavigator;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class D_TodoListView {
	
	private final SceneNavigator navigator;
	private final String listId;
	private final String listName;

	public D_TodoListView(SceneNavigator navigator, String listId, String listName) {
		this.navigator = navigator;
		this.listId = listId;
		this.listName = listName;
	}

	public Scene createScene() {
		Label title = new Label("Todo List: " + listName);
		title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
		
		Button mainMenuButton = new Button("Main Menu");
		mainMenuButton.setOnAction(e -> navigator.showMainMenu());

		Button viewTasksButton = new Button("View Tasks");
		viewTasksButton.setOnAction(e -> navigator.showTaskView(listId, listName));

		Label info = new Label("List ID: " + listId);
		
		VBox root = new VBox(16, title, info, viewTasksButton, mainMenuButton);
		root.setAlignment(Pos.CENTER);
		root.setStyle("-fx-padding: 24;");

		return new Scene(root, 520, 360);
	}
}
