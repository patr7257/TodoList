package dk.dtu;

import dk.dtu.shared.Config;
import dk.dtu.scenes.A_WelcomeScreen;
import dk.dtu.scenes.B_LoginScreen;
import dk.dtu.scenes.C_MainMenu;
import dk.dtu.scenes.D_TodoListView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.util.ArrayDeque;
import java.util.Deque;

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

    private enum ViewType { WELCOME, LOGIN, MAIN_MENU, TODO_LIST }

    private record NavState(ViewType type, String listId, String listName) {
        static NavState welcome() { return new NavState(ViewType.WELCOME, null, null); }
        static NavState login() { return new NavState(ViewType.LOGIN, null, null); }
        static NavState mainMenu() { return new NavState(ViewType.MAIN_MENU, null, null); }
        static NavState todoList(String listId, String listName) { return new NavState(ViewType.TODO_LIST, listId, listName); }
    }

    private final Deque<NavState> backStack = new ArrayDeque<>();
    private final Deque<NavState> forwardStack = new ArrayDeque<>();
    private NavState currentState;
    private String pendingMainMenuMessage;

    // Constructor
    public SceneNavigator(Stage stage) {
        this.stage = stage;
        this.sidebar = new Sidebar(this);
        this.darkModeManager = new DarkModeManager();
        
        // Register connection error handler
        Config.setConnectionErrorHandler(this::handleConnectionError);
        
        // Connect sidebar theme toggle to dark mode manager
        sidebar.setOnThemeChange(() -> darkModeManager.toggleDarkMode());

        // Back button always navigates history
        sidebar.setBackButtonAction(this::goBack);
        updateBackButtonVisibility();
        
        // Don't start notification listener yet - wait for user to connect to server
    }

    public void goBack() {
        if (backStack.isEmpty() || currentState == null) {
            return;
        }
        forwardStack.push(currentState);
        NavState prev = backStack.pop();
        navigateTo(prev, false);
    }

    public void goForward() {
        if (forwardStack.isEmpty() || currentState == null) {
            return;
        }
        backStack.push(currentState);
        NavState next = forwardStack.pop();
        navigateTo(next, false);
    }

    public void reloadMainMenu() {
        if (currentState != null && currentState.type == ViewType.MAIN_MENU) {
            navigateTo(currentState, false);
        }
    }

    public void reloadTodoList() {
        if (currentState != null && currentState.type == ViewType.TODO_LIST) {
            navigateTo(currentState, false);
        }
    }

    private void updateBackButtonVisibility() {
        // Disable back button on login/welcome screens (can't go back without being logged in)
        if (currentState != null && (currentState.type == ViewType.LOGIN || currentState.type == ViewType.WELCOME)) {
            sidebar.disableBackButton();
        } else if (backStack.isEmpty()) {
            sidebar.disableBackButton();
        } else {
            sidebar.enableBackButton();
        }
    }

    private void navigateTo(NavState next, boolean pushHistory) {
        if (pushHistory && currentState != null) {
            backStack.push(currentState);
            forwardStack.clear();
        }
        currentState = next;
        updateBackButtonVisibility();

        switch (next.type) {
            case WELCOME -> renderWelcome();
            case LOGIN -> renderLogin();
            case MAIN_MENU -> {
                String msg = pendingMainMenuMessage;
                pendingMainMenuMessage = null;
                renderMainMenu(msg);
            }
            case TODO_LIST -> renderTodoList(next.listId, next.listName);
        }
    }

    // Start the notification listener thread (called after successful server connection)
    public void connectToServer() {
        // Stop any existing listener first
        stopNotificationListener();
        
        System.out.println("[SceneNavigator] Starting notification listener for " + Config.getClientBaseUri());
        notificationListener = new NotificationListener(
                Config.getNotificationsUri(),
                this::refreshCurrentView
        );
        notificationThread = new Thread(notificationListener, "notification-listener");
        notificationThread.setDaemon(true);
        notificationThread.start();
    }
    
    private void stopNotificationListener() {
        if (notificationListener != null && notificationThread != null && notificationThread.isAlive()) {
            System.out.println("[SceneNavigator] Stopping existing notification listener...");
            notificationListener.stop();
            try {
                notificationThread.join(2000); // Wait up to 2 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[SceneNavigator] Interrupted while stopping notification listener");
            }
        }
    }
    
    private void handleConnectionError(Exception e) {
        System.err.println("[SceneNavigator] Connection error detected: " + e.getMessage());
        
        // Stop notification listener since connection is lost
        stopNotificationListener();
        
        // Show error dialog on JavaFX thread
        javafx.application.Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Connection Lost");
            alert.setHeaderText("Lost connection to server");
            alert.setContentText("The connection to the server was lost. This could be due to:\n" +
                    "• Server crashed or stopped\n" +
                    "• Network connection lost\n" +
                    "• Server IP changed\n\n" +
                    "Click 'Reconnect' to connect to a server again.");
            
            javafx.scene.control.ButtonType reconnectButton = new javafx.scene.control.ButtonType("Reconnect");
            javafx.scene.control.ButtonType cancelButton = javafx.scene.control.ButtonType.CANCEL;
            
            alert.getButtonTypes().setAll(reconnectButton, cancelButton);
            
            alert.showAndWait().ifPresent(response -> {
                if (response == reconnectButton) {
                    // Go back to welcome screen for reconnection
                    showWelcome();
                }
            });
        });
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
        System.out.println("[SceneNavigator] Shutting down client...");
        
        // Stop notification listener first
        stopNotificationListener();
        
        // Then notify server of disconnect
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
        
        // Use content scene dimensions or fallback to reasonable defaults.
        // Important: add sidebar width so the center content doesn't overlap/cover it.
        double sidebarWidth = sidebar.getPrefWidth() > 0 ? sidebar.getPrefWidth() : 70;
        double contentWidth = contentScene.getWidth() > 0 ? contentScene.getWidth() : 900;
        double width = contentWidth + sidebarWidth;
        double height = contentScene.getHeight() > 0 ? contentScene.getHeight() : 600;
        
        // Create new scene with the BorderPane
        Scene sceneWithSidebar = new Scene(root, width, height);

        // Mouse extra buttons: back/forward navigation
        sceneWithSidebar.addEventFilter(MouseEvent.MOUSE_PRESSED, evt -> {
            if (evt.getButton() == MouseButton.BACK) {
                goBack();
                evt.consume();
            } else if (evt.getButton() == MouseButton.FORWARD) {
                goForward();
                evt.consume();
            }
        });
        
        // Apply theme through dark mode manager
        darkModeManager.setScene(sceneWithSidebar);
        
        stage.setTitle(title);
        stage.setScene(sceneWithSidebar);
    }

    // A: Show welcome scene (first thing when ClientApp starts)
    public void showWelcome() {
        navigateTo(NavState.welcome(), true);
    }

    private void renderWelcome() {
        currentMainMenu = null;
        currentTodoListView = null;
        sidebar.setColumnFilterButtonAction(null);
        sidebar.setListFilterButtonAction(null);
        Scene scene = new A_WelcomeScreen(this).createScene();
        setScene(scene, "Patrick & Elines Amazing Huske-System");
    }

    // B: Show login screen
    public void showLogin() {
        navigateTo(NavState.login(), true);
    }

    private void renderLogin() {
        if (currentUser != null) {
            System.out.println("Logged out: " + currentUser);
            currentUser = null;
        }
        currentMainMenu = null;
        currentTodoListView = null;
        sidebar.setColumnFilterButtonAction(null);
        sidebar.setListFilterButtonAction(null);
        Scene scene = new B_LoginScreen(this).createScene();
        setScene(scene, "Login - Patrick & Elines Amazing Huske-System");
    }

    // C: Show main menu
    public void showMainMenu() {
        navigateTo(NavState.mainMenu(), true);
    }

    private void renderMainMenu(String loginMessage) {
        currentMainMenu = loginMessage == null ? new C_MainMenu(this) : new C_MainMenu(this, loginMessage);
        currentTodoListView = null;
        sidebar.setColumnFilterButtonAction(() -> {
            if (currentMainMenu != null) {
                currentMainMenu.openColumnsDialog();
            }
        });
        sidebar.setListFilterButtonAction(() -> {
            if (currentMainMenu != null) {
                currentMainMenu.openFilterDialog();
            }
        });
        Scene scene = currentMainMenu.createScene();
        setScene(scene, "Main Menu - Patrick & Elines Amazing Huske-System");
    }

    public void showMainMenuWithMessage(String loginMessage) {
        pendingMainMenuMessage = loginMessage;
        navigateTo(NavState.mainMenu(), true);
    }

    // D: Show todo list view for selected list
    public void showTodoList(String listId, String listName) {
        navigateTo(NavState.todoList(listId, listName), true);
    }

    private void renderTodoList(String listId, String listName) {
        currentMainMenu = null;
        currentTodoListView = new D_TodoListView(this, listId, listName);
        sidebar.setColumnFilterButtonAction(() -> {
            if (currentTodoListView != null) {
                currentTodoListView.openColumnsDialog();
            }
        });
        sidebar.setListFilterButtonAction(() -> {
            if (currentTodoListView != null) {
                currentTodoListView.openFilterDialog();
            }
        });
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