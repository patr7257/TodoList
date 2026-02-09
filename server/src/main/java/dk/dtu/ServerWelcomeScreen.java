package dk.dtu;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

public class ServerWelcomeScreen {
    private final Stage stage;
    private final ServerConfigDialog.ServerStartCallback startCallback;

    public ServerWelcomeScreen(Stage stage, ServerConfigDialog.ServerStartCallback startCallback) {
        this.stage = stage;
        this.startCallback = startCallback;
    }

    public Scene createScene() {
        // Logo (same as client for consistency)
        ImageView logo = new ImageView();
        try {
            logo.setImage(new Image(getClass().getResourceAsStream("/icons/todo.png")));
            logo.setFitWidth(70);
            logo.setPreserveRatio(true);
        } catch (Exception e) {
            // If logo not found, continue without it
            System.out.println("Logo not found for server");
        }

        Label title = new Label("TodoList Server");
        title.getStyleClass().add("server-title");
        title.setStyle("-fx-font-size: 42px; -fx-font-weight: 700; -fx-text-fill: #333333;");

        Label subtitle = new Label("Configure and start your server");
        subtitle.getStyleClass().add("server-subtitle");
        subtitle.setStyle("-fx-font-size: 18px; -fx-text-fill: #4a4a4a; -fx-font-weight: 500;");

        Button startConfigButton = new Button("Configure Server");
        startConfigButton.getStyleClass().add("primary-button");
        startConfigButton.setDefaultButton(true);
        startConfigButton.setOnAction(e -> handleConfigure());

        // Group logo + title
        VBox headerBox = new VBox(8, logo, title);
        headerBox.setAlignment(Pos.CENTER);

        // Root layout
        VBox root = new VBox(20, headerBox, subtitle, startConfigButton);
        root.setAlignment(Pos.CENTER);
        root.getStyleClass().add("server-screen");
        root.setPadding(new Insets(40, 20, 40, 20));
        root.setStyle("-fx-background-color: #e8ebf0;");

        Scene scene = new Scene(root, 900, 600);
        
        // Apply stylesheet
        try {
            scene.getStylesheets().add(getClass().getResource("/server.css").toExternalForm());
        } catch (Exception e) {
            System.out.println("Warning: Could not load server.css");
        }

        // Fade in animation
        FadeTransition ft = new FadeTransition(Duration.millis(600), root);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();

        return scene;
    }

    private void handleConfigure() {
        ServerConfigDialog.ServerConfig config = ServerConfigDialog.show(stage);
        if (config != null && startCallback != null) {
            startCallback.onStart(config);
        }
    }
}
