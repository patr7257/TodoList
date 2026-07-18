package dk.dtu;

import atlantafx.base.theme.PrimerLight;
import dk.dtu.shared.Config;
import javafx.application.Application;
import javafx.stage.Stage;

// Main JavaFX application class
public class ClientApp extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // Global AtlantaFX theme (Primer base), light by default.
        // Set before the stage is shown so every window, including dialogs, picks it up.
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        // Set initial window size
        primaryStage.setWidth(970);
        primaryStage.setHeight(600);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(500);
        
        // Show welcome screen first (connection happens from there)
        SceneNavigator navigator = new SceneNavigator(primaryStage);
        navigator.showWelcome();
        
        // Handle application shutdown
        primaryStage.setOnCloseRequest(event -> {
            System.out.println("Client disconnected");
            navigator.shutdown();
            javafx.application.Platform.exit();
            System.exit(0);
        });

        primaryStage.show();

        // First scene is up: quietly check for a newer release in the background.
        navigator.checkForUpdatesOnLaunch();
    }
}