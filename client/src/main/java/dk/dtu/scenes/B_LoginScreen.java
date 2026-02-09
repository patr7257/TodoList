package dk.dtu.scenes;

import dk.dtu.shared.Config;
import dk.dtu.MainUserConfig;
import dk.dtu.methods.Users;
import dk.dtu.SceneNavigator;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.application.Platform;

import java.util.ArrayList;
import java.util.List;

public class B_LoginScreen {

    private final SceneNavigator navigator;

    public B_LoginScreen(SceneNavigator navigator) {
        this.navigator = navigator;
    }

    public Scene createScene() {
        Label title = new Label("Login");
        title.getStyleClass().add("login-title");

        // Dynamically create main user buttons based on configuration
        List<Button> mainUserButtons = new ArrayList<>();
        List<String> mainUsers = MainUserConfig.getMainUsers();
        
        for (int i = 0; i < mainUsers.size(); i++) {
            String username = mainUsers.get(i);
            String color = (i == 0) ? MainUserConfig.getMainUser1Color() : MainUserConfig.getMainUser2Color();
            
            Button userButton = new Button(username);
            userButton.getStyleClass().add(MainUserConfig.getStyleClassForUser(username));
            userButton.setMinWidth(220);
            userButton.setMinHeight(80);
            
            // Apply custom color with inline style (higher priority than CSS)
            String buttonStyle = String.format(
                "-fx-font-size: 24px; -fx-font-weight: bold; " +
                "-fx-background-color: linear-gradient(to bottom, %s, derive(%s, -15%%)); " +
                "-fx-text-fill: white; " +
                "-fx-background-radius: 15; " +
                "-fx-border-color: #000000; " +
                "-fx-border-width: 3px; " +
                "-fx-border-radius: 15; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 18, 0, 0, 4); " +
                "-fx-cursor: hand;",
                color, color
            );
            userButton.setStyle(buttonStyle);
            
            userButton.setOnAction(e -> loginAsUser(username, userButton));
            mainUserButtons.add(userButton);
        }

        // Place main user buttons (1 or 2) side by side
        HBox mainUserButtonContainer = new HBox(20);
        mainUserButtonContainer.setAlignment(Pos.CENTER);
        mainUserButtonContainer.getChildren().addAll(mainUserButtons);
        
        // Container box for main login section (title + user buttons)
        VBox mainLoginBox = new VBox(20, title, mainUserButtonContainer);
        mainLoginBox.setAlignment(Pos.CENTER);
        mainLoginBox.getStyleClass().add("login-box");
        mainLoginBox.setStyle("-fx-background-color: white; -fx-border-color: #d0d0d0; -fx-border-width: 2px; " +
                              "-fx-border-radius: 15; -fx-background-radius: 15; -fx-padding: 30; " +
                              "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 15, 0, 0, 3);");
        mainLoginBox.setMaxWidth(600);

        // Action buttons section - only create and login as other
        Button createUserButton = new Button("Create new user...");
        createUserButton.getStyleClass().add("action-button");
        createUserButton.setPrefWidth(250);
        createUserButton.setOnAction(evt -> showCreateUserDialog(createUserButton));

        // "Login as other user" button
        Button otherUserButton = new Button("Login as other user");
        otherUserButton.getStyleClass().add("action-button");
        otherUserButton.setPrefWidth(250);
        otherUserButton.setOnAction(e -> showOtherUserDialog(otherUserButton));

        VBox actionButtons = new VBox(12, createUserButton, otherUserButton);
        actionButtons.setAlignment(Pos.CENTER);

        VBox root = new VBox(30, mainLoginBox, actionButtons);
        root.setAlignment(Pos.CENTER);
        root.setFillWidth(false);  
        root.getStyleClass().add("login-root");
        root.setPadding(new Insets(40));

        return new Scene(root, 900, 600);
    }

    private void loginAsUser(String username, Button button) {
        button.setDisable(true);
        new Thread(() -> {
            try {
                Users.loginExistingUser(username, Config.getUsersUri(), (message) -> {
                    navigator.setCurrentUser(username);
                    navigator.showMainMenuWithMessage(message);
                    Platform.runLater(() -> button.setDisable(false));
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Login Error");
                    alert.setHeaderText("Could not login");
                    alert.setContentText("User '" + username + "' does not exist in the system. Please create this user first.");
                    alert.showAndWait();
                    button.setDisable(false);
                });
                ex.printStackTrace();
            }
        }, "login-main-user").start();
    }

    private void showOtherUserDialog(Button triggerButton) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Login as Other User");
        dialog.setHeaderText("Select a user to login");

        // Tooltip explaining the star
        Label infoLabel = new Label("ℹ Users with a * are main users");
        infoLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 12px; -fx-font-style: italic;");

        ComboBox<String> userComboBox = new ComboBox<>();
        userComboBox.setPromptText("Select a user...");
        userComboBox.setPrefWidth(300);
        
        // Load existing users into the combo box
        Users.loadUsersIntoComboBox(userComboBox, Config.getUsersUri());

        VBox content = new VBox(15, infoLabel, new Label("Choose user:"), userComboBox);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(15));
        
        dialog.getDialogPane().setContent(content);
        
        ButtonType loginBtn = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginBtn, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn != loginBtn) return;
            
            String username = userComboBox.getValue();
            if (username == null || username.isBlank()) return;
            
            // Strip star if present
            username = username.replace(" *", "");

            triggerButton.setDisable(true);
            final String finalUsername = username;
            new Thread(() -> {
                try {
                    Users.loginExistingUser(finalUsername, Config.getUsersUri(), (message) -> {
                        navigator.setCurrentUser(finalUsername);
                        navigator.showMainMenuWithMessage(message);
                        Platform.runLater(() -> triggerButton.setDisable(false));
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> triggerButton.setDisable(false));
                    ex.printStackTrace();
                }
            }, "login-other-user").start();
        });
    }

    private void showCreateUserDialog(Button triggerButton) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Create new user");
        dialog.setHeaderText("Create a new username (max 15 characters)");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Enter new username");
        usernameField.setPrefWidth(320);

        usernameField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && newValue.length() > 15) {
                usernameField.setText(oldValue);
            }
        });

        VBox content = new VBox(10, new Label("Username:"), usernameField);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(15));
        dialog.getDialogPane().setContent(content);

        ButtonType createAndLogin = new ButtonType("Create & Login", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createAndLogin, ButtonType.CANCEL);

        javafx.scene.Node okButton = dialog.getDialogPane().lookupButton(createAndLogin);
        okButton.setDisable(true);

        usernameField.textProperty().addListener((obs, oldVal, newVal) -> {
            okButton.setDisable(newVal == null || newVal.trim().isBlank());
        });

        dialog.showAndWait().ifPresent(btn -> {
            if (btn != createAndLogin) return;

            String username = usernameField.getText() != null ? usernameField.getText().trim() : "";
            if (username.isBlank()) return;

            triggerButton.setDisable(true);
            new Thread(() -> {
                try {
                    Users.createNewUser(username, Config.getUsersUri(), (message) -> {
                        navigator.setCurrentUser(username);
                        navigator.showMainMenuWithMessage(message);
                        Platform.runLater(() -> triggerButton.setDisable(false));
                    }, (error) -> {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setTitle("Error");
                            alert.setHeaderText("Cannot create user");
                            alert.setContentText(error);
                            alert.showAndWait();
                            triggerButton.setDisable(false);
                        });
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> triggerButton.setDisable(false));
                    ex.printStackTrace();
                }
            }, "create-user").start();
        });
    }
}
