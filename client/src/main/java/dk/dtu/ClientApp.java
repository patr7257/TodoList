package dk.dtu;

import atlantafx.base.theme.PrimerLight;
import dk.dtu.net.ApiSession;
import javafx.application.Application;
import javafx.stage.Stage;

import java.util.Optional;

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

        // Default to the last API server we used, if any, so Config picks it up.
        // An explicitly passed -Dtodolist.api.url always wins over the saved value.
        if (System.getProperty("todolist.api.url") == null) {
            ServerPrefs.savedApiBaseUrl().ifPresent(url -> System.setProperty("todolist.api.url", url));
        }

        // Set initial window size
        primaryStage.setWidth(970);
        primaryStage.setHeight(600);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(500);
        
        // Restore a persisted session if we have one, so a relaunch stays logged
        // in; otherwise start at the welcome screen. An invalid/expired token is
        // handled gracefully: the first state fetch returns 401 and routes back
        // to the login screen via the auth-expired handler.
        SceneNavigator navigator = new SceneNavigator(primaryStage);
        Optional<String> savedToken = ServerPrefs.savedToken();
        if (savedToken.isPresent()) {
            ApiSession.get().configure(savedToken.get());
            navigator.setCurrentUser(ServerPrefs.savedEmail().orElse("User"));
            navigator.connectToServer();
            navigator.showMainMenu();
        } else {
            navigator.showWelcome();
        }
        
        // Handle application shutdown
        primaryStage.setOnCloseRequest(event -> {
            System.out.println("Client disconnected");
            navigator.shutdown();
            javafx.application.Platform.exit();
            System.exit(0);
        });

        primaryStage.show();

        // Now that the native window exists, match the OS title bar to the app
        // theme (starts in light mode). Re-applied by SceneNavigator on theme
        // toggles and scene changes.
        dk.dtu.ui.WindowChrome.applyDarkTitleBar(primaryStage, false);

        // First scene is up: quietly check for a newer release in the background.
        navigator.checkForUpdatesOnLaunch();
    }
}