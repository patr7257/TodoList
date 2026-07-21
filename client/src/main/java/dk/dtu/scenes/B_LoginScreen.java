package dk.dtu.scenes;

import atlantafx.base.theme.Styles;
import dk.dtu.SceneNavigator;
import dk.dtu.ServerPrefs;
import dk.dtu.net.ApiModels.LoginResponse;
import dk.dtu.net.ApiSession;
import dk.dtu.shared.Config;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * Email + password login against the shared todo HTTP API. On success the
 * bearer token is stored (in memory via {@link ApiSession} and persisted via
 * {@link ServerPrefs} so a relaunch stays signed in), the state poller starts,
 * and the app routes to the main menu.
 */
public class B_LoginScreen {

    private final SceneNavigator navigator;

    public B_LoginScreen(SceneNavigator navigator) {
        this.navigator = navigator;
    }

    public Scene createScene() {
        Label title = new Label("Sign in");
        title.getStyleClass().add("login-title");

        Label serverLabel = new Label("Server: " + Config.getApiBaseUrl());
        serverLabel.getStyleClass().add("settings-note");

        TextField emailField = new TextField();
        emailField.setPromptText("Email");
        emailField.setPrefWidth(320);
        emailField.setMaxWidth(320);
        ServerPrefs.savedEmail().ifPresent(emailField::setText);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setPrefWidth(320);
        passwordField.setMaxWidth(320);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("status-error");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(320);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        Button signInButton = new Button("Sign in");
        signInButton.setDefaultButton(true);
        signInButton.getStyleClass().addAll(Styles.ACCENT, Styles.LARGE);
        signInButton.setPrefWidth(320);

        Runnable submit = () -> attemptLogin(emailField, passwordField, errorLabel, signInButton);
        signInButton.setOnAction(e -> submit.run());
        passwordField.setOnAction(e -> submit.run());

        VBox box = new VBox(14, title, serverLabel, emailField, passwordField, errorLabel, signInButton);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("login-box");
        box.setMaxWidth(420);

        VBox root = new VBox(box);
        root.setAlignment(Pos.CENTER);
        root.setFillWidth(false);
        root.getStyleClass().add("login-root");
        root.setPadding(new Insets(40));

        return new Scene(root, 900, 600);
    }

    private void attemptLogin(TextField emailField, PasswordField passwordField,
                              Label errorLabel, Button signInButton) {
        String email = emailField.getText() != null ? emailField.getText().trim() : "";
        String password = passwordField.getText() != null ? passwordField.getText() : "";

        if (email.isEmpty() || password.isEmpty()) {
            showError(errorLabel, "Enter both email and password.");
            return;
        }

        signInButton.setDisable(true);
        signInButton.setText("Signing in...");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        new Thread(() -> {
            try {
                // Make sure the client points at the currently configured base URL.
                ApiSession.get().configure(null);
                LoginResponse res = ApiSession.get().login(email, password);
                String name = (res.user() != null && res.user().name() != null)
                        ? res.user().name() : email;

                // Persist the session so a relaunch stays logged in.
                ServerPrefs.saveApiBaseUrl(Config.getApiBaseUrl());
                ServerPrefs.saveAuth(res.token(), email);

                Platform.runLater(() -> {
                    signInButton.setDisable(false);
                    signInButton.setText("Sign in");
                    navigator.setCurrentUser(name);
                    navigator.connectToServer(); // start the state poller
                    navigator.showMainMenuWithMessage("Logged in as " + name);
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    signInButton.setDisable(false);
                    signInButton.setText("Sign in");
                    showError(errorLabel, friendlyError(ex));
                });
            }
        }, "api-login").start();
    }

    private static String friendlyError(Exception ex) {
        if (ex instanceof dk.dtu.net.ApiException api) {
            if (api.isUnauthorized()) {
                return "Invalid email or password.";
            }
            return "Sign in failed (server said HTTP " + api.status() + ").";
        }
        return "Could not reach the server. Check the server URL and your connection.";
    }

    private static void showError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}
