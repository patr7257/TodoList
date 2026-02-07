package dk.dtu;

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
        // Ask which server to connect to before initializing background listeners.
        ClientConnectDialog.ConnectionSettings settings = ClientConnectDialog.show(primaryStage);
        if (settings == null) {
            javafx.application.Platform.exit();
            return;
        }

        System.setProperty("todolist.server.ip", settings.serverIp());
        System.setProperty("todolist.port", Integer.toString(settings.port()));

        // Set initial window size
        primaryStage.setWidth(970);
        primaryStage.setHeight(600);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(500);
        
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