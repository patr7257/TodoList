package dk.dtu.scenes;

import dk.dtu.shared.Config;
import dk.dtu.methods.*;
import dk.dtu.SceneNavigator;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class C_MainMenu {
    
    private final SceneNavigator navigator;
    private final String loginMessage;

    private final ListView<Helpers.ListEntry> listsView = new ListView<>();
    private final Button logoutButton = new Button("Logout");
    private final Button createToDoListButton = new Button("Create To Do List");
    
    public C_MainMenu(SceneNavigator navigator) {
        this(navigator, null);
    }

    public C_MainMenu(SceneNavigator navigator, String loginMessage) {
        this.navigator = navigator;
        this.loginMessage = loginMessage;
    }

    public Scene createScene() {
        Label title = new Label("Available todo lists");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label userLabel = new Label("Logged in as: " + navigator.getCurrentUser());

        Label tempMessageLabel = new Label();
        if (loginMessage != null && !loginMessage.isBlank()) {
            tempMessageLabel.setText(loginMessage);
            tempMessageLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");

            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
                Platform.runLater(() -> tempMessageLabel.setText(""));
            }, "login-message-timer").start();
        }

        logoutButton.setOnAction(e -> navigator.showLogin());

        createToDoListButton.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Create To Do List");
            dialog.setHeaderText("Enter a name for the new list:");
            dialog.setContentText("Name:");

            dialog.showAndWait().ifPresent(name -> {
                if (name == null || name.isBlank()) {
                    return;
                }
                createToDoListButton.setDisable(true);
                new Thread(() -> {
                    try {
                        Lists.createTodoList(Config.getRequestsUri(), Config.getResponsesUri(), name);
                        javafx.application.Platform.runLater(() -> createToDoListButton.setDisable(false));
                    } catch (Exception ex) {
                        javafx.application.Platform.runLater(() -> createToDoListButton.setDisable(false));
                        ex.printStackTrace();
                    }
                }, "create-list").start();
            });
        });

        listsView.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            Helpers.ListEntry selected = listsView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            navigator.showTodoList(selected.id, selected.name);
        });

        HBox actions = new HBox(10, logoutButton, createToDoListButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(
                10,
                title,
                userLabel,
                tempMessageLabel,
                actions,
                listsView);
        root.setPadding(new Insets(12));

        Lists.loadTodoLists(listsView, Config.getTodoListsUri());

        return new Scene(root, 520, 420);
    }
    
    /**
     * Auto-refresh lists when notification received from server.
     * Called by SceneNavigator when server broadcasts list changes.
     */
    public void autoRefreshLists() {
        Lists.loadTodoLists(listsView, Config.getTodoListsUri());
    }
}