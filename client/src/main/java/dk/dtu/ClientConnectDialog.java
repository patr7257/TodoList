package dk.dtu;

import atlantafx.base.theme.Styles;
import dk.dtu.net.TodoApiClient;
import dk.dtu.shared.Config;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Dialog for choosing the todo API server the client connects to. Replaces the
 * old jSpace IP/port + network-scan dialog: the client now speaks HTTP, so all
 * that is needed is the API base URL (origin), for example
 * {@code https://api.todolist.patrickrobel.dk}. Includes a best-effort "Test"
 * that pings the API without needing valid credentials.
 */
public final class ClientConnectDialog {

    /** The chosen API origin (no trailing /api/todo). */
    public record ApiSettings(String baseUrl) {}

    private ClientConnectDialog() {}

    public static ApiSettings show(Stage owner) {
        Stage stage = new Stage();
        stage.setTitle("Connect to TodoList API");
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);

        Label heading = new Label("Server API base URL");
        heading.getStyleClass().add("settings-section-title");

        TextField urlField = new TextField(Config.getApiBaseUrl());
        urlField.setPromptText("https://api.todolist.patrickrobel.dk");
        urlField.setPrefWidth(420);

        Label note = new Label("Enter the API origin (without the /api/todo path). "
                + "The default is the public production server.");
        note.getStyleClass().add("settings-note");
        note.setWrapText(true);

        Label statusLabel = new Label("");
        statusLabel.getStyleClass().add("status-label");

        Button testButton = new Button("Test");
        testButton.getStyleClass().add(Styles.BUTTON_OUTLINED);

        Button connectBtn = new Button("Save");
        connectBtn.getStyleClass().add(Styles.SUCCESS);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);

        testButton.setOnAction(e -> {
            String url = urlField.getText() != null ? urlField.getText().trim() : "";
            if (url.isBlank()) {
                statusLabel.setText("Enter a URL first.");
                return;
            }
            statusLabel.setText("Testing " + url + "...");
            testButton.setDisable(true);
            new Thread(() -> {
                boolean reachable = new TodoApiClient(url, null).ping();
                Platform.runLater(() -> {
                    testButton.setDisable(false);
                    statusLabel.setText(reachable
                            ? "Server reachable."
                            : "Could not reach the server (you can still save and try signing in).");
                });
            }, "api-test").start();
        });

        HBox buttonsRow = new HBox(12, testButton, connectBtn, cancelBtn);
        buttonsRow.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(15, heading, urlField, note, statusLabel, buttonsRow);
        root.getStyleClass().add("config-panel");
        root.setPadding(new Insets(24));
        root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        StackPane container = new StackPane(root);
        container.setStyle("-fx-background-color: -color-bg-default;");
        StackPane.setAlignment(root, Pos.CENTER);

        final ApiSettings[] result = new ApiSettings[1];

        connectBtn.setOnAction(e -> {
            String url = urlField.getText() != null ? urlField.getText().trim() : "";
            if (url.isBlank()) {
                statusLabel.setText("Enter a URL first.");
                return;
            }
            result[0] = new ApiSettings(url);
            stage.close();
        });

        cancelBtn.setOnAction(e -> {
            result[0] = null;
            stage.close();
        });

        stage.setOnCloseRequest(e -> result[0] = null);

        Scene scene = new Scene(container, 620, 300);
        DarkModeManager.applyBrand(scene.getStylesheets());
        stage.setScene(scene);
        stage.setMinWidth(620);
        stage.setMinHeight(300);

        stage.showAndWait();
        return result[0];
    }
}
