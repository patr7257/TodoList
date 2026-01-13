package dk.dtu.scenes;

import dk.dtu.Config;
import dk.dtu.Methods;
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
    private final String loginMessage; // besked vist kort efter login

    private final Label statusLabel = new Label("Loading...");
    private final ListView<Methods.ListEntry> listsView = new ListView<>();
    private final Button refreshButton = new Button("Refresh");
    private final Button logoutButton = new Button("Logout");
    private final Button pingButton = new Button("Ping server");
    private final Button createToDoListButton = new Button("Create To Do List");
    
    // Bruges hvis man går til MainMenu uden specifik login-besked
    public C_MainMenu(SceneNavigator navigator) {
        this(navigator, null);
    }

    // Bruges når man lige er logget ind (med besked fra login)
    public C_MainMenu(SceneNavigator navigator, String loginMessage) {
        this.navigator = navigator;
        this.loginMessage = loginMessage;
    }

    public Scene createScene() {
        Label title = new Label("Available todo lists");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label userLabel = new Label("Logged in as: " + navigator.getCurrentUser());

        // Label til midlertidig login-besked
        Label tempMessageLabel = new Label();
        if (loginMessage != null && !loginMessage.isBlank()) {
            tempMessageLabel.setText(loginMessage);
            tempMessageLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");

            // Fjern beskeden efter 2 sekunder
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
                Platform.runLater(() -> tempMessageLabel.setText(""));
            }, "login-message-timer").start();
        }

        refreshButton.setOnAction(e -> Methods.loadTodoLists(
                statusLabel, refreshButton, listsView, Config.TODO_LISTS_URI));

        logoutButton.setOnAction(e -> navigator.showLogin());

        pingButton.setOnAction(e -> Methods.sendPing(
                statusLabel, pingButton, Config.REQUESTS_URI));

        createToDoListButton.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Create To Do List");
            dialog.setHeaderText("Enter a name for the new list:");
            dialog.setContentText("Name:");

            dialog.showAndWait().ifPresent(name -> {
                if (name == null || name.isBlank()) {
                    Methods.setStatus(tempMessageLabel, "Enter a name");
                    return;
                }
                Methods.createToDoList(
                        tempMessageLabel,
                        createToDoListButton,
                        Config.REQUESTS_URI,
                        Config.RESPONSES_URI,
                        refreshButton,
                        listsView,
                        Config.TODO_LISTS_URI,
                        name);
            });
        });

        listsView.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            Methods.ListEntry selected = listsView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            navigator.showTodoList(selected.id, selected.name);
        });

        HBox actions = new HBox(10, refreshButton, pingButton, logoutButton, createToDoListButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        // Vi tilføjer tempMessageLabel mellem userLabel og statusLabel
        VBox root = new VBox(
                10,
                title,
                userLabel,
                tempMessageLabel,
                statusLabel,
                actions,
                listsView);
        root.setPadding(new Insets(12));

        Methods.loadTodoLists(statusLabel, refreshButton, listsView, Config.TODO_LISTS_URI);

        return new Scene(root, 520, 420);
    }
}