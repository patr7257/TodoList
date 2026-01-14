package dk.dtu;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * JavaFX client
 * - Scenes + controllers (login, lists overview, tasks view)
 * - Navigation (in SceneNavigator)
 * - UI states (current user, selected list etc)
 */
public class ClientApp extends Application {
    
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        SceneNavigator navigator = new SceneNavigator(primaryStage);
        navigator.showWelcome();
        primaryStage.show();
    }
}
