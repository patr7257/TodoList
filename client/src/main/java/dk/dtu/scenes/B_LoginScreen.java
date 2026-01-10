package dk.dtu.scenes;

import dk.dtu.SceneNavigator;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class B_LoginScreen {
    
    private final SceneNavigator navigator;

    public B_LoginScreen(SceneNavigator navigator) {
        this.navigator = navigator;
    }

    public Scene createScene() {
        Label title = new Label("Login");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

		Button loginButton = new Button("Login here");
		loginButton.setDefaultButton(true);
		loginButton.setOnAction(e -> navigator.showMainMenu());

        Button backButton = new Button("Back to Welcome Screen");
        backButton.setOnAction(e -> navigator.showWelcome());

        VBox root = new VBox(14, title, loginButton, backButton);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 24;");

        return new Scene(root, 520, 360);
    }

}