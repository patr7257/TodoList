package dk.dtu.scenes;

import dk.dtu.shared.Config;
import dk.dtu.methods.Users;
import dk.dtu.SceneNavigator;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public class B_LoginScreen {

    private final SceneNavigator navigator;

    public B_LoginScreen(SceneNavigator navigator) {
        this.navigator = navigator;
    }

    public Scene createScene() {
        Label title = new Label("Login");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label usernameLabel = new Label("Username:");
        TextField usernameField = new TextField();
        usernameField.setPromptText("Enter username (e.g. alice)");

        Button loginButton = new Button("Login");
        loginButton.setDefaultButton(true);
        loginButton.setOnAction(evt -> {
            String username = usernameField.getText();
            if (username.isBlank()) {
                return;
            }
            loginButton.setDisable(true);
            new Thread(() -> {
                try {
                    Users.autoLoginUser(username, Config.getUsersUri(), (message) -> {
                        navigator.setCurrentUser(username);
                        navigator.showMainMenuWithMessage(message);
                        javafx.application.Platform.runLater(() -> loginButton.setDisable(false));
                    });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> loginButton.setDisable(false));
                    ex.printStackTrace();
                }
            }, "login").start();
        });

        Button backButton = new Button("Back to Welcome Screen");
        backButton.setOnAction(e -> navigator.showWelcome());

        VBox root = new VBox(10, title, usernameLabel, usernameField, loginButton, backButton);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 24;");

        return new Scene(root, 520, 360);
    }
}