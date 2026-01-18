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
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class C_MainMenu {

    private final SceneNavigator navigator;
    private final String loginMessage;

    private final ListView<Helpers.ListEntry> listsView = new ListView<>();
    private final Button logoutButton = new Button();

    public C_MainMenu(SceneNavigator navigator) {
        this(navigator, null);
    }

    public C_MainMenu(SceneNavigator navigator, String loginMessage) {
        this.navigator = navigator;
        this.loginMessage = loginMessage;
    }

    public Scene createScene() {
        // Small logout icon button
        ImageView logoutIcon = new ImageView(new Image(getClass().getResourceAsStream("/Icons/gobackicon.png")));
        logoutIcon.setFitWidth(32);
        logoutIcon.setFitHeight(32);
        logoutButton.setGraphic(logoutIcon);
        logoutButton.getStyleClass().add("go-back-button");
        logoutButton.setOnAction(e -> {
            String username = navigator.getCurrentUser();
            if (username == null || username.isEmpty()) {
                username = "unknown";
            }
            System.out.println("User logged out: " + username);
            
            // Send logout notification to server (capture username before clearing)
            final String usernameToSend = username;
            new Thread(() -> {
                try {
                    org.jspace.RemoteSpace requests = new org.jspace.RemoteSpace(Config.getRequestsUri());
                    requests.put(dk.dtu.shared.TupleSpaces.CMD_USER_LOGOUT,
                            java.util.UUID.randomUUID().toString(),
                            usernameToSend, 
                            "", 
                            "", 
                            "");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }, "logout").start();
            
            navigator.showLogin();
        }); // only set once

        // Put logout icon top-right in a full-width bar
        HBox topBar = new HBox(logoutButton);
        topBar.setAlignment(Pos.TOP_RIGHT);
        topBar.setPadding(new Insets(0, 0, 10, 0));
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

        // Header row for columns: List | Completion | Tasks | Overdue | Delete
        Label nameHeader = new Label("List Name");
        nameHeader.setPrefWidth(300);
        nameHeader.setAlignment(Pos.CENTER);
        nameHeader.setStyle("-fx-font-weight: bold;");

        Label completionHeader = new Label("Completion");
        completionHeader.setPrefWidth(180);
        completionHeader.setAlignment(Pos.CENTER);
        completionHeader.setStyle("-fx-font-weight: bold;");

        Label tasksHeader = new Label("Tasks");
        tasksHeader.setPrefWidth(80);
        tasksHeader.setAlignment(Pos.CENTER);
        tasksHeader.setStyle("-fx-font-weight: bold;");

        Label overdueHeader = new Label("Overdue");
        overdueHeader.setPrefWidth(90);
        overdueHeader.setAlignment(Pos.CENTER);
        overdueHeader.setStyle("-fx-font-weight: bold;");

        Label deleteHeader = new Label("");
        deleteHeader.setPrefWidth(50);

        HBox header = new HBox(15, nameHeader, completionHeader, tasksHeader, overdueHeader, deleteHeader);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setMaxWidth(830);
        header.getStyleClass().add("list-header");

        // When a list item is clicked, open that list
        listsView.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY|| e.getClickCount() != 2) {
                return;
            }
            Helpers.ListEntry selected = listsView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            navigator.showTodoList(selected.id, selected.name);
        });

        // Make table match task view dimensions
        listsView.setPrefWidth(830);
        listsView.setMaxWidth(830);
        listsView.setPrefHeight(400);

        // Custom cells: List Name | Completion | Tasks | Overdue | Delete button
        listsView.setCellFactory(lv -> new ListCell<>() {

            private final Label nameLabel = new Label();
            private final javafx.scene.control.ProgressBar progressBar = new javafx.scene.control.ProgressBar();
            private final Label tasksLabel = new Label();
            private final javafx.scene.shape.Circle statusCircle = new javafx.scene.shape.Circle(8);
            private final javafx.scene.layout.StackPane statusPane = new javafx.scene.layout.StackPane(statusCircle);
            private final Button deleteButton = new Button();

            private final HBox row = new HBox(15, nameLabel, progressBar, tasksLabel, statusPane, deleteButton);

            {
                // Make columns line up with header widths
                nameLabel.setPrefWidth(300);
                progressBar.setPrefWidth(180);
                progressBar.setMaxWidth(180);
                progressBar.setPrefHeight(20);
                tasksLabel.setPrefWidth(80);
                tasksLabel.setAlignment(Pos.CENTER);
                tasksLabel.setMaxWidth(80);
                statusPane.setPrefWidth(90);
                statusPane.setAlignment(Pos.CENTER);
                statusPane.setMaxWidth(90);
                deleteButton.setPrefWidth(50);
                
                ImageView deleteIcon = new ImageView(new Image(getClass().getResourceAsStream("/Icons/deleteicon.png")));
                deleteIcon.setFitWidth(28);
                deleteIcon.setFitHeight(28);
                deleteButton.setGraphic(deleteIcon);

                row.setAlignment(Pos.CENTER_LEFT);

                nameLabel.getStyleClass().add("list-col-name");
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
                            Lists.deleteTodoList(Config.getRequestsUri(), Config.getResponsesUri(), item.id);
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
                nameLabel.setTooltip(new Tooltip(item.name));
                
                // Status circle: green if no overdue tasks, red if any overdue
                if (item.overdueTaskCount > 0) {
                    statusCircle.setFill(javafx.scene.paint.Color.rgb(220, 53, 69)); // Red
                    Tooltip circleTooltip = new Tooltip("This list contains overdue tasks");
                    Tooltip.install(statusPane, circleTooltip);
                } else {
                    statusCircle.setFill(javafx.scene.paint.Color.rgb(40, 167, 69)); // Green
                    Tooltip circleTooltip = new Tooltip("This list has no overdue tasks");
                    Tooltip.install(statusPane, circleTooltip);
                }
                
                // Progress bar with color coding
                double progress = item.completionPercentage / 100.0;
                progressBar.setProgress(progress);
                
                // Color code the progress bar
                String barColor;
                if (progress >= 0.8) {
                    barColor = "#28a745"; // Green for 80%+
                } else if (progress >= 0.5) {
                    barColor = "#ffc107"; // Yellow/amber for 50-79%
                } else if (progress >= 0.3) {
                    barColor = "#fd7e14"; // Orange for 30-49%
                } else {
                    barColor = "#dc3545"; // Red for <30%
                }
                progressBar.setStyle("-fx-accent: " + barColor + ";");
                progressBar.setTooltip(new Tooltip(item.completionPercentage + "% complete"));
                
                tasksLabel.setText(String.valueOf(item.taskCount));

                setGraphic(row);
            }
        });

        // "+ Create new list" link under the list
        Hyperlink createLink = new Hyperlink("+  Create new list");
        createLink.getStyleClass().add("create-link");
        createLink.setOnAction(e -> showCreateListDialog());

        VBox titleSection = new VBox(5, title, userLabel, tempMessageLabel);
        titleSection.setAlignment(Pos.TOP_CENTER);
        
        VBox root = new VBox(
                topBar,
                titleSection,
                new Label(""), // gap spacer
                header,
                listsView,
                createLink);
        root.setSpacing(10);
        root.setPadding(new Insets(24));
        root.setAlignment(Pos.TOP_CENTER);
        root.setFillWidth(true);           // <- important: let children stretch horizontally
        root.getStyleClass().add("mainmenu-root");

        // Initial load of lists (completion is now included in list tuples)
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