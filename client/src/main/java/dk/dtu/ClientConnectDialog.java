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
 * Dialog for choosing the servers the client connects to. There are two, because
 * they are separate deployments: the todo API origin (for example
 * {@code https://api.todolist.patrickrobel.dk}) that holds the data, and the web
 * origin (for example {@code https://patrickrobel.dk}) that owns sign in since
 * it went browser mediated (issue #51). Includes a best-effort "Test" that pings
 * the API without needing valid credentials.
 */
public final class ClientConnectDialog {

    /**
     * The chosen origins: {@code baseUrl} is the API (no trailing /api/todo),
     * {@code webBaseUrl} is the website that hosts the sign-in page.
     */
    public record ApiSettings(String baseUrl, String webBaseUrl) {}

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

        Label webHeading = new Label("Website base URL (sign in)");
        webHeading.getStyleClass().add("settings-section-title");

        TextField webUrlField = new TextField(Config.getWebBaseUrl());
        webUrlField.setPromptText("https://patrickrobel.dk");
        webUrlField.setPrefWidth(420);

        Label webNote = new Label("Sign in happens in your browser on this site. "
                + "Point it at a local dev server only if you are developing the website.");
        webNote.getStyleClass().add("settings-note");
        webNote.setWrapText(true);

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

        VBox root = new VBox(15, heading, urlField, note,
                webHeading, webUrlField, webNote, statusLabel, buttonsRow);
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
            String webUrl = webUrlField.getText() != null ? webUrlField.getText().trim() : "";
            if (webUrl.isBlank()) {
                // Blank means "use the default", never an empty origin: an empty
                // web origin would leave sign in with nowhere to go.
                webUrl = Config.DEFAULT_WEB_BASE_URL;
            }
            result[0] = new ApiSettings(url, webUrl);
            stage.close();
        });

        cancelBtn.setOnAction(e -> {
            result[0] = null;
            stage.close();
        });

        stage.setOnCloseRequest(e -> result[0] = null);

        Scene scene = new Scene(container, 620, 460);
        DarkModeManager.applyBrand(scene.getStylesheets());
        stage.setScene(scene);
        stage.setMinWidth(620);
        stage.setMinHeight(460);

        stage.showAndWait();
        return result[0];
    }
}
