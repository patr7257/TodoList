package dk.dtu.scenes;

import dk.dtu.Methods;
import dk.dtu.SceneNavigator;
import dk.dtu.TupleSpaces;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public class B_LoginScreen {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 9001;
    private static final String USERS_URI = "tcp://" + HOST + ":" + PORT + "/" + TupleSpaces.USERS + "?keep";

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

        Label statusLabel = new Label();

        Button loginButton = new Button("Login");
        loginButton.setDefaultButton(true);
        loginButton.setOnAction(evt -> {
            Methods.autoLoginUser(
                    statusLabel,
                    loginButton,
                    usernameField.getText(),
                    USERS_URI,
                    (message) -> {
                        navigator.setCurrentUser(usernameField.getText());
                        navigator.showMainMenuWithMessage(message);
                    });
        });

        Button backButton = new Button("Back to Welcome Screen");
        backButton.setOnAction(e -> navigator.showWelcome());

        VBox root = new VBox(10, title, usernameLabel, usernameField, loginButton, statusLabel, backButton);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 24;");

        return new Scene(root, 520, 360);
    }
}