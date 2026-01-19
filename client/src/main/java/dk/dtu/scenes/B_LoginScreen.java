package dk.dtu.scenes;

import dk.dtu.shared.Config;
import dk.dtu.methods.Users;
import dk.dtu.SceneNavigator;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class B_LoginScreen {

    private final SceneNavigator navigator;

    public B_LoginScreen(SceneNavigator navigator) {
        this.navigator = navigator;
    }

    public Scene createScene() {
        Label title = new Label("Login");
        title.getStyleClass().add("login-title");

        // Section for existing users
        Label existingUserLabel = new Label("Login as existing user:");
        existingUserLabel.getStyleClass().add("login-section-label");

        ComboBox<String> userComboBox = new ComboBox<>();
        userComboBox.setPromptText("Select a user...");
        userComboBox.setPrefWidth(300);
        userComboBox.getStyleClass().add("login-textfield");
        
        // Load existing users into the combo box
        Users.loadUsersIntoComboBox(userComboBox, Config.getUsersUri());

        Button loginExistingButton = new Button("Login");
        loginExistingButton.setDefaultButton(true);
        loginExistingButton.setOnAction(evt -> {
            String username = userComboBox.getValue();
            if (username == null || username.isBlank()) {
                return;
            }
            loginExistingButton.setDisable(true);
            new Thread(() -> {
                try {
                    Users.loginExistingUser(username, Config.getUsersUri(), (message) -> {
                        navigator.setCurrentUser(username);
                        navigator.showMainMenuWithMessage(message);
                        javafx.application.Platform.runLater(() -> loginExistingButton.setDisable(false));
                    });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> loginExistingButton.setDisable(false));
                    ex.printStackTrace();
                }
            }, "login-existing").start();
        });

        VBox existingUserSection = new VBox(10, existingUserLabel, userComboBox, loginExistingButton);
        existingUserSection.setAlignment(Pos.CENTER);
        existingUserSection.setStyle("-fx-padding: 20; -fx-border-color: #cccccc; -fx-border-width: 1; -fx-border-radius: 5; -fx-background-radius: 5;");
        existingUserSection.setPrefWidth(350);

        // Section for creating new user
        Label newUserLabel = new Label("Create new user:");
        newUserLabel.getStyleClass().add("login-section-label");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Enter new username");
        usernameField.setPrefWidth(300);
        usernameField.getStyleClass().add("login-textfield");
        
        // Limit username to 15 characters
        usernameField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && newValue.length() > 15) {
                usernameField.setText(oldValue);
            }
        });

        Button createUserButton = new Button("Create & Login");
        createUserButton.setOnAction(evt -> {
            String username = usernameField.getText();
            if (username.isBlank()) {
                return;
            }
            createUserButton.setDisable(true);
            new Thread(() -> {
                try {
                    Users.createNewUser(username, Config.getUsersUri(), (message) -> {
                        navigator.setCurrentUser(username);
                        navigator.showMainMenuWithMessage(message);
                        javafx.application.Platform.runLater(() -> createUserButton.setDisable(false));
                    }, (error) -> {
                        javafx.application.Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setTitle("Error");
                            alert.setHeaderText("Cannot create user");
                            alert.setContentText(error);
                            alert.showAndWait();
                            createUserButton.setDisable(false);
                        });
                    });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> createUserButton.setDisable(false));
                    ex.printStackTrace();
                }
            }, "create-user").start();
        });

        VBox newUserSection = new VBox(10, newUserLabel, usernameField, createUserButton);
        newUserSection.setAlignment(Pos.CENTER);
        newUserSection.setStyle("-fx-padding: 20; -fx-border-color: #cccccc; -fx-border-width: 1; -fx-border-radius: 5; -fx-background-radius: 5;");
        newUserSection.setPrefWidth(350);

        // Place both sections side by side
        HBox loginSections = new HBox(30, existingUserSection, newUserSection);
        loginSections.setAlignment(Pos.CENTER);

        Button backButton = new Button("Back to Welcome Screen");
        backButton.setOnAction(e -> navigator.showWelcome());

        VBox root = new VBox(30, title, loginSections, backButton);
        root.setAlignment(Pos.CENTER);
        root.setFillWidth(false);  
        root.getStyleClass().add("login-root");

        return new Scene(root, 900, 600);

    }
}