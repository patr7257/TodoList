package dk.dtu;

import dk.dtu.shared.Config;
import dk.dtu.scenes.A_WelcomeScreen;
import dk.dtu.scenes.B_LoginScreen;
import dk.dtu.scenes.C_MainMenu;
import dk.dtu.scenes.D_TodoListView;
import dk.dtu.scenes.E_TaskView;
import javafx.scene.Scene;
import javafx.stage.Stage;

// JavaFX navigation between scenes (Add more methods for new scenes)
public class SceneNavigator {

    private final Stage stage;
    private String currentUser;
    private Thread notificationThread;
    private NotificationListener notificationListener;

    // References to current scene for auto-refresh
    private C_MainMenu currentMainMenu;
    private D_TodoListView currentTodoListView;
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
        } else if (currentTodoListView != null) {
            currentTodoListView.autoRefreshTasks();
        } else if (currentTaskView != null) {
            currentTaskView.autoRefreshTasks();
        }
        // TODO: add more scene types here as needed
    }

    public void shutdown() {
        try {
            org.jspace.RemoteSpace requests = new org.jspace.RemoteSpace(Config.getRequestsUri());
            String requestId = java.util.UUID.randomUUID().toString();
            requests.put(dk.dtu.shared.TupleSpaces.CMD_CLIENT_DISCONNECT,
                    requestId,
                    currentUser != null ? currentUser : "",
                    "",
                    "",
                    "");
        } catch (Exception e) {
            // ignore
        }

        if (notificationListener != null) {
            notificationListener.stop();
        }
        if (notificationThread != null) {
            notificationThread.interrupt();
        }
    }

    // Helper: always apply stylesheet + title in one place
    private void setScene(Scene scene, String title) {
        scene.getStylesheets().add(
                getClass().getResource("/common.css").toExternalForm()
        );
        stage.setTitle(title);
        stage.setScene(scene);
    }

    // A: Show welcome scene (first thing when ClientApp starts)
    public void showWelcome() {
        currentMainMenu = null;
        currentTodoListView = null;
        currentTaskView = null;
        Scene scene = new A_WelcomeScreen(this).createScene();
        setScene(scene, "What ToDo");
    }

    // B: Show login screen
    public void showLogin() {
        if (currentUser != null) {
            System.out.println("Logged out: " + currentUser);
            currentUser = null;
        }
        currentMainMenu = null;
        currentTodoListView = null;
        currentTaskView = null;
        Scene scene = new B_LoginScreen(this).createScene();
        setScene(scene, "Login - What ToDo");
    }

    // C: Show main menu
    public void showMainMenu() {
        currentMainMenu = new C_MainMenu(this);
        currentTodoListView = null;
        currentTaskView = null;
        Scene scene = currentMainMenu.createScene();
        setScene(scene, "Main Menu - What ToDo");
    }

    public void showMainMenuWithMessage(String loginMessage) {
        currentMainMenu = new C_MainMenu(this, loginMessage);
        currentTodoListView = null;
        currentTaskView = null;
        Scene scene = currentMainMenu.createScene();
        setScene(scene, "Main Menu - What ToDo");
    }

    // D: Show todo list view for selected list
    public void showTodoList(String listId, String listName) {
        currentMainMenu = null;
        currentTodoListView = new D_TodoListView(this, listId, listName);
        currentTaskView = null;
        Scene scene = currentTodoListView.createScene();
        setScene(scene, "Todo List - " + listName);
    }

    // E: Show task view for selected list (manages all tasks)
    public void showTaskView(String listId, String listName) {
        currentMainMenu = null;
        currentTodoListView = null;
        currentTaskView = new E_TaskView(this, listId, listName);
        Scene scene = currentTaskView.createScene();
        setScene(scene, "Task Manager - " + listName);
    }

    public void setCurrentUser(String username) {
        this.currentUser = username;
    }

    public String getCurrentUser() {
        return currentUser;
    }
}