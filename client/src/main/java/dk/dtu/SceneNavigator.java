package dk.dtu;

import dk.dtu.shared.Config;
import dk.dtu.scenes.A_WelcomeScreen;
import dk.dtu.scenes.B_LoginScreen;
import dk.dtu.scenes.C_MainMenu;
import dk.dtu.scenes.D_TodoListView;
import dk.dtu.scenes.E_TaskView;
import javafx.stage.Stage;

// JavaFX navigation between scenes (Add more methods for new scenes)
public class SceneNavigator {

    private final Stage stage;
    private String currentUser;
    private Thread notificationThread;
    private NotificationListener notificationListener;
    
    // References to current scene for auto-refresh
    private C_MainMenu currentMainMenu;
    private E_TaskView currentTaskView;

    // Constructor
    public SceneNavigator(Stage stage) {
        this.stage = stage;
        startNotificationListener();
    }
    
    // Start the notification listener thread
    private void startNotificationListener() {
        notificationListener = new NotificationListener(
            Config.getNotificationsUri(),
            this::refreshCurrentView
        );
        notificationThread = new Thread(notificationListener, "notification-listener");
        notificationThread.setDaemon(true);
        notificationThread.start();
    }
    
    private void refreshCurrentView() {
        // Refresh whichever scene is currently active
        if (currentMainMenu != null) {
            currentMainMenu.autoRefreshLists();
        } else if (currentTaskView != null) {
            currentTaskView.autoRefreshTasks();
        }
        // TODO: add more scene types here as needed
    }
    
    public void shutdown() {
        if (notificationListener != null) {
            notificationListener.stop();
        }
        if (notificationThread != null) {
            notificationThread.interrupt();
        }
    }

    // A: Show welcome scene (first thing when ClientApp starts)
    public void showWelcome() {
        currentMainMenu = null;
        currentTaskView = null;
        stage.setTitle("Welcome");
        stage.setScene(new A_WelcomeScreen(this).createScene());
    }

    // B: Show login screen
    public void showLogin() {
        currentMainMenu = null;
        currentTaskView = null;
        stage.setTitle("Login");
        stage.setScene(new B_LoginScreen(this).createScene());
    }

    // C: Show main menu
    public void showMainMenu() {
        currentMainMenu = new C_MainMenu(this);
        currentTaskView = null;
        stage.setTitle("Main Menu");
        stage.setScene(currentMainMenu.createScene());
    }

    // D: Show todo list view for selected list
    public void showTodoList(String listId, String listName) {
        currentMainMenu = null;
        currentTaskView = null;
        stage.setTitle("Todo List - " + listName);
        stage.setScene(new D_TodoListView(this, listId, listName).createScene());
    }

    // E: Show task view for selected list (manages all tasks)
    public void showTaskView(String listId, String listName) {
        currentMainMenu = null;
        currentTaskView = new E_TaskView(this, listId, listName);
        stage.setTitle("Task Manager - " + listName);
        stage.setScene(currentTaskView.createScene());
    }

    public void setCurrentUser(String username) {
        this.currentUser = username;
    }

    public String getCurrentUser() {
        return currentUser;
    }
    
    public void showMainMenuWithMessage(String loginMessage) {
        currentMainMenu = new C_MainMenu(this, loginMessage);
        currentTaskView = null;
        stage.setTitle("Main Menu");
        stage.setScene(currentMainMenu.createScene());
    }
}