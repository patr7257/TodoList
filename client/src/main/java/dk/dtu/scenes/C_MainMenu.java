package dk.dtu.scenes;

import dk.dtu.SceneNavigator;
import dk.dtu.methods.Helpers;
import dk.dtu.methods.Lists;
import dk.dtu.shared.Config;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class C_MainMenu {

    private final SceneNavigator navigator;
    private final String loginMessage;

    private final ListView<Helpers.ListEntry> listsView = new ListView<>();
    private final Button logoutButton = new Button("↩"); // logout icon

    public C_MainMenu(SceneNavigator navigator) {
        this(navigator, null);
    }

    public C_MainMenu(SceneNavigator navigator, String loginMessage) {
        this.navigator = navigator;
        this.loginMessage = loginMessage;
    }

    public Scene createScene() {
        // Small logout icon button
        logoutButton.getStyleClass().add("logout-icon");
        logoutButton.setOnAction(e -> navigator.showLogin()); // only set once

        // Put logout icon top-right in a full-width bar
        HBox topBar = new HBox(logoutButton);
        topBar.setAlignment(Pos.CENTER_RIGHT);
        topBar.setPadding(new Insets(10, 20, 0, 0));
        topBar.setMaxWidth(Double.MAX_VALUE); // allow it to stretch

        Label title = new Label("Available todo lists");
        title.getStyleClass().add("mainmenu-title");

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

        // Header row for columns: List | Completion | Tasks | Delete
        Label nameHeader = new Label("List");
        nameHeader.setPrefWidth(250);

        Label completionHeader = new Label("Completion");
        completionHeader.setPrefWidth(120);

        Label tasksHeader = new Label("Tasks");
        tasksHeader.setPrefWidth(120);

        Label deleteHeader = new Label("Delete");
        deleteHeader.setPrefWidth(100);

        HBox header = new HBox(30, nameHeader, completionHeader, tasksHeader, deleteHeader);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setMaxWidth(700);
        header.getStyleClass().add("list-header");

        // When a list item is clicked, open that list
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

        // Make table wider
        listsView.setPrefWidth(700);
        listsView.setMaxWidth(700);

        // Custom cells: List | Completion | Tasks | Delete button
        listsView.setCellFactory(lv -> new ListCell<>() {

            private final Label nameLabel = new Label();
            private final Label completionLabel = new Label();
            private final Label tasksLabel = new Label();
            private final Button deleteButton = new Button("✖");

            private final HBox row = new HBox(nameLabel, completionLabel, tasksLabel, deleteButton);

            {
                // Make columns line up with header widths
                nameLabel.setPrefWidth(300);
                completionLabel.setPrefWidth(120);
                tasksLabel.setPrefWidth(120);
                deleteButton.setPrefWidth(100);

                row.setAlignment(Pos.CENTER_LEFT);

                nameLabel.getStyleClass().add("list-col-name");
                completionLabel.getStyleClass().add("list-col-status");
                tasksLabel.getStyleClass().add("list-col-count");
                deleteButton.getStyleClass().add("list-col-delete-button");

                // Make sure clicking delete does not trigger list row click
                deleteButton.addEventFilter(MouseEvent.MOUSE_PRESSED, evt -> {
                    evt.consume(); // stop it from bubbling to the ListView

                    Helpers.ListEntry item = getItem();
                    if (item == null)
                        return;

                    System.out.println("Delete list: " + item.id + " - " + item.name);

                    new Thread(() -> {
                        try {
                            // TODO: call real delete when you have it:
                            // Lists.deleteTodoList(Config.getRequestsUri(), Config.getResponsesUri(), item.id);
                            Platform.runLater(() -> Lists.loadTodoLists(listsView, Config.getTodoListsUri()));
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }, "delete-list").start();
                });
            }

            @Override
            protected void updateItem(Helpers.ListEntry item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                nameLabel.setText(item.name);
                completionLabel.setText("–"); // placeholder
                tasksLabel.setText("–");      // placeholder

                setGraphic(row);
            }
        });

        // "+ Create new list" link under the list
        Hyperlink createLink = new Hyperlink("+  Create new list");
        createLink.getStyleClass().add("create-link");
        createLink.setOnAction(e -> showCreateListDialog());

        VBox root = new VBox(
                topBar,
                title,
                userLabel,
                tempMessageLabel,
                header,
                listsView,
                createLink);
        root.setSpacing(20);
        root.setPadding(new Insets(24));
        root.setAlignment(Pos.TOP_CENTER);
        root.setFillWidth(true);           // <- important: let children stretch horizontally
        root.getStyleClass().add("mainmenu-root");

        // Initial load of lists
        Lists.loadTodoLists(listsView, Config.getTodoListsUri());

        return new Scene(root, 900, 600);
    }

    private void showCreateListDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create New To Do List");
        dialog.setHeaderText("Enter name for the new list:");
        dialog.setContentText("Name:");

        dialog.showAndWait().ifPresent(name -> {
            if (name == null || name.isBlank())
                return;

            new Thread(() -> {
                try {
                    Lists.createTodoList(
                            Config.getRequestsUri(),
                            Config.getResponsesUri(),
                            name);
                    Platform.runLater(() -> Lists.loadTodoLists(listsView, Config.getTodoListsUri()));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }, "create-list").start();
        });
    }

    /**
     * Auto-refresh lists when notification received from server.
     * Called by SceneNavigator when server broadcasts list changes.
     */
    public void autoRefreshLists() {
        Lists.loadTodoLists(listsView, Config.getTodoListsUri());
    }
}