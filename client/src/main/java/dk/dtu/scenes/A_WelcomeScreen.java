package dk.dtu.scenes;

import dk.dtu.SceneNavigator;
import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class A_WelcomeScreen {
	private final SceneNavigator navigator;

	public A_WelcomeScreen(SceneNavigator navigator) {
		this.navigator = navigator;
	}

	public Scene createScene() {

    // Logo
    ImageView logo = new ImageView(new Image(
            getClass().getResource("/icons/todo.png").toExternalForm()));
    logo.setFitWidth(70);
    logo.setPreserveRatio(true);

    Label title = new Label("What ToDo");
    title.getStyleClass().add("welcome-title");

    Label tagline = new Label("Organize your day. One task at a time.");
    tagline.getStyleClass().add("welcome-tagline");

    Button loginButton = new Button("Sign in");
    loginButton.setDefaultButton(true);
    loginButton.getStyleClass().add("primary-button");
    loginButton.setOnAction(e -> navigator.showLogin());

    // NEW: group logo + title with tight spacing
    VBox headerBox = new VBox(5, logo, title);   // 5 px between logo and title
    headerBox.setAlignment(Pos.CENTER);

    // Root layout: spacing mainly between header, subtitle, button, tagline
    VBox root = new VBox(15, headerBox, loginButton, tagline);
    root.setAlignment(Pos.CENTER);
    root.getStyleClass().add("welcome-screen");
	root.setPadding(new Insets(10, 0, 0, 0));

    Scene scene = new Scene(root, 900, 600);

    FadeTransition ft = new FadeTransition(Duration.millis(600), root);
    ft.setFromValue(0);
    ft.setToValue(1);
    ft.play();
    return scene;
}
}