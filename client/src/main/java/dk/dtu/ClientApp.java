package dk.dtu;

import javafx.application.Application;
import javafx.stage.Stage;

// Main JavaFX application class
public class ClientApp extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
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
    }
}