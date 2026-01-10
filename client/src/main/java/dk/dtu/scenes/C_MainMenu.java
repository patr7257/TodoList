package dk.dtu.scenes;

import dk.dtu.Methods;
import dk.dtu.SceneNavigator;
import dk.dtu.TupleSpaces;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class C_MainMenu {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 9001;
    private static final String TODO_LISTS_URI = "tcp://" + HOST + ":" + PORT + "/todoLists?keep";
    private static final String REQUESTS_URI = "tcp://" + HOST + ":" + PORT + "/" + TupleSpaces.REQUESTS + "?keep";

    private final SceneNavigator navigator;

    private final Label statusLabel = new Label("Loading...");
    private final ListView<Methods.ListEntry> listsView = new ListView<>();
    private final Button refreshButton = new Button("Refresh");
    private final Button logoutButton = new Button("Logout");
    private final Button pingButton = new Button("Ping server");

    public C_MainMenu(SceneNavigator navigator) {
        this.navigator = navigator;
    }

    public Scene createScene() {
        Label title = new Label("Available todo lists");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        refreshButton.setOnAction(e -> Methods.loadTodoLists(statusLabel, refreshButton, listsView, TODO_LISTS_URI));
		logoutButton.setOnAction(e -> navigator.showLogin());
        pingButton.setOnAction(e -> Methods.sendPing(statusLabel, pingButton, REQUESTS_URI));

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

        HBox actions = new HBox(10, refreshButton, pingButton, logoutButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10, title, statusLabel, actions, listsView);
        root.setPadding(new Insets(12));

        Methods.loadTodoLists(statusLabel, refreshButton, listsView, TODO_LISTS_URI);
        return new Scene(root, 520, 420);
    }
    
}