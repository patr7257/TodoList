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
		Label title = new Label("Todo List");
		title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

		Button mainMenuButton = new Button("Main menu");
		mainMenuButton.setOnAction(e -> navigator.showMainMenu());

		Label selected = new Label("Selected: " + listId + " - " + listName);

		Label placeholder = new Label("(Tasks view will go here next)");
		placeholder.setStyle("-fx-opacity: 0.8;");

		VBox root = new VBox(12, title, mainMenuButton, selected, placeholder);
		root.setAlignment(Pos.CENTER);
		root.setStyle("-fx-padding: 24;");

		return new Scene(root, 520, 360);
	}
}
