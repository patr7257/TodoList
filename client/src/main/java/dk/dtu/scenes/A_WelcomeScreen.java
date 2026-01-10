package dk.dtu.scenes;

import dk.dtu.SceneNavigator;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class A_WelcomeScreen {
	private final SceneNavigator navigator;

	public A_WelcomeScreen(SceneNavigator navigator) {
		this.navigator = navigator;
	}

	public Scene createScene() {
		Label title = new Label("Welcome to our Todo-List management system");
		title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

		Button loginButton = new Button("Login");
		loginButton.setDefaultButton(true);
		loginButton.setOnAction(e -> navigator.showLogin());

		VBox root = new VBox(18, title, loginButton);
		root.setAlignment(Pos.CENTER);
		root.setStyle("-fx-padding: 24;");

		return new Scene(root, 520, 360);
	}
    
}
