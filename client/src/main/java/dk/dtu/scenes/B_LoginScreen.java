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
        title.getStyleClass().add("login-title");

        // Main section: login as existing user
        Label existingUserLabel = new Label("Login as user");
        existingUserLabel.getStyleClass().add("login-section-label");

        ComboBox<String> userComboBox = new ComboBox<>();
        userComboBox.setPromptText("Select a user...");
        userComboBox.setPrefWidth(300);
        userComboBox.getStyleClass().add("login-textfield");
        
        // Load existing users into the combo box
        Users.loadUsersIntoComboBox(userComboBox, Config.getUsersUri());

        Button loginExistingButton = new Button("Login");
        loginExistingButton.setDefaultButton(true);
        loginExistingButton.getStyleClass().add("primary-button");
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
        existingUserSection.setPrefWidth(420);

        Button deleteUserButton = new Button("Delete selected user");
        deleteUserButton.setOnAction(evt -> {
            String username = userComboBox.getValue();
            if (username == null || username.isBlank()) {
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Delete User");
            confirm.setHeaderText("Delete user '" + username + "'?");
            confirm.setContentText("This is only allowed if the user does not own any lists.");

            confirm.showAndWait().ifPresent(response -> {
                if (response != ButtonType.OK) return;
                deleteUserButton.setDisable(true);
                new Thread(() -> {
                    try {
                        Users.deleteUser(Config.getRequestsUri(), Config.getResponsesUri(), username);
                        javafx.application.Platform.runLater(() -> {
                            Users.loadUsersIntoComboBox(userComboBox, Config.getUsersUri());
                            deleteUserButton.setDisable(false);
                        });
                    } catch (Exception ex) {
                        javafx.application.Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setTitle("Cannot delete user");
                            alert.setHeaderText("User could not be deleted");
                            alert.setContentText(ex.getMessage());
                            alert.showAndWait();
                            deleteUserButton.setDisable(false);
                        });
                    }
                }, "delete-user").start();
            });
        });

        Button createUserPopupButton = new Button("Create new user...");
        createUserPopupButton.setOnAction(evt -> {
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

                createUserPopupButton.setDisable(true);
                new Thread(() -> {
                    try {
                        Users.createNewUser(username, Config.getUsersUri(), (message) -> {
                            navigator.setCurrentUser(username);
                            navigator.showMainMenuWithMessage(message);
                            javafx.application.Platform.runLater(() -> createUserPopupButton.setDisable(false));
                        }, (error) -> {
                            javafx.application.Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR);
                                alert.setTitle("Error");
                                alert.setHeaderText("Cannot create user");
                                alert.setContentText(error);
                                alert.showAndWait();
                                createUserPopupButton.setDisable(false);
                            });
                        });
                    } catch (Exception ex) {
                        javafx.application.Platform.runLater(() -> createUserPopupButton.setDisable(false));
                        ex.printStackTrace();
                    }
                }, "create-user").start();
            });
        });

        VBox root = new VBox(18, title, existingUserSection, deleteUserButton, createUserPopupButton);
        root.setAlignment(Pos.CENTER);
        root.setFillWidth(false);  
        root.getStyleClass().add("login-root");

        return new Scene(root, 900, 600);

    }
}