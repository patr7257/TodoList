package dk.dtu;

import dk.dtu.scenes.A_WelcomeScreen;
import dk.dtu.scenes.B_LoginScreen;
import dk.dtu.scenes.C_MainMenu;
import dk.dtu.scenes.D_TodoListView;
import javafx.stage.Stage;

// JavaFX navigation between scenes (Add more methods for new scenes)
public class SceneNavigator {

    private final Stage stage;
    private String currentUser;

    // Constructor
    public SceneNavigator(Stage stage) {
        this.stage = stage;
    }

    // A: Show welcome scene (first thing when ClientApp starts)
    public void showWelcome() {
        stage.setTitle("Welcome");
        stage.setScene(new A_WelcomeScreen(this).createScene());
    }

    // B: Show login screen
    public void showLogin() {
        stage.setTitle("Login");
        stage.setScene(new B_LoginScreen(this).createScene());
    }

    // C: Show main menu
    public void showMainMenu() {
        stage.setTitle("Main Menu");
        stage.setScene(new C_MainMenu(this).createScene());
    }

    // D: Show todo list view for selected list
    public void showTodoList(String listId, String listName) {
        stage.setTitle("Todo List");
        stage.setScene(new D_TodoListView(this, listId, listName).createScene());
    }
    public void setCurrentUser(String username) {
        this.currentUser = username;
    }

    public String getCurrentUser() {
        return currentUser;
    }
    public void showMainMenuWithMessage(String loginMessage) {
    stage.setTitle("Main Menu");
    stage.setScene(new C_MainMenu(this, loginMessage).createScene());
}
}