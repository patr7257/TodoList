package dk.dtu;

import dk.dtu.shared.Config;
import dk.dtu.scenes.A_WelcomeScreen;
import dk.dtu.scenes.B_LoginScreen;
import dk.dtu.scenes.C_MainMenu;
import dk.dtu.scenes.D_TodoListView;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
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

    // Sidebar and dark mode
    private Sidebar sidebar;
    private DarkModeManager darkModeManager;

    // Constructor
    public SceneNavigator(Stage stage) {
        this.stage = stage;
        this.sidebar = new Sidebar(this);
        this.darkModeManager = new DarkModeManager();
        
        // Connect sidebar theme toggle to dark mode manager
        sidebar.setOnThemeChange(() -> darkModeManager.toggleDarkMode());
        
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

    // Helper: always apply stylesheet + title in one place, and wrap content with sidebar
    private void setScene(Scene contentScene, String title) {
        // Create a BorderPane to hold the content and sidebar
        BorderPane root = new BorderPane();
        root.setCenter(contentScene.getRoot());
        root.setRight(sidebar);
        
        // Use content scene dimensions or fallback to reasonable defaults
        double width = contentScene.getWidth() > 0 ? contentScene.getWidth() : 970;
        double height = contentScene.getHeight() > 0 ? contentScene.getHeight() : 600;
        
        // Create new scene with the BorderPane
        Scene sceneWithSidebar = new Scene(root, width, height);
        
        // Apply theme through dark mode manager
        darkModeManager.setScene(sceneWithSidebar);
        
        stage.setTitle(title);
        stage.setScene(sceneWithSidebar);
    }

    // A: Show welcome scene (first thing when ClientApp starts)
    public void showWelcome() {
        currentMainMenu = null;
        currentTodoListView = null;
        sidebar.hideBackButton();
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
        sidebar.setBackButtonAction(() -> showWelcome());
        Scene scene = new B_LoginScreen(this).createScene();
        setScene(scene, "Login - What ToDo");
    }

    // C: Show main menu
    public void showMainMenu() {
        currentMainMenu = new C_MainMenu(this);
        currentTodoListView = null;
        sidebar.setBackButtonAction(() -> showLogin());
        Scene scene = currentMainMenu.createScene();
        setScene(scene, "Main Menu - What ToDo");
    }

    public void showMainMenuWithMessage(String loginMessage) {
        currentMainMenu = new C_MainMenu(this, loginMessage);
        currentTodoListView = null;
        sidebar.setBackButtonAction(() -> showLogin());
        Scene scene = currentMainMenu.createScene();
        setScene(scene, "Main Menu - What ToDo");
    }

    // D: Show todo list view for selected list
    public void showTodoList(String listId, String listName) {
        currentMainMenu = null;
        currentTodoListView = new D_TodoListView(this, listId, listName);
        sidebar.setBackButtonAction(() -> showMainMenu());
        Scene scene = currentTodoListView.createScene();
        setScene(scene, "Todo List - " + listName);
    }

    public void setCurrentUser(String username) {
        this.currentUser = username;
    }

    public String getCurrentUser() {
        return currentUser;
    }
}