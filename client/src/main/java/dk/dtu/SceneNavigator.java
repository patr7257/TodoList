package dk.dtu;

import atlantafx.base.theme.Styles;
import dk.dtu.shared.Config;
import dk.dtu.scenes.A_WelcomeScreen;
import dk.dtu.scenes.B_LoginScreen;
import dk.dtu.scenes.C_MainMenu;
import dk.dtu.scenes.D_TodoListView;
import dk.dtu.update.ReleaseInfo;
import dk.dtu.update.UpdateChecker;
import dk.dtu.update.UpdateFlow;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

// JavaFX navigation between scenes (Add more methods for new scenes)
public class SceneNavigator {

    private static final String APP_TITLE = "TodoList Management System";

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

    // Pinned update banner, shown at the top of the window when a newer release
    // is available. Null when no update is offered or it was dismissed. Kept as
    // a field so it survives scene navigation (re-applied in setScene).
    private HBox updateBanner;
    private boolean updateCheckStarted;

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
        
        // Connect sidebar theme toggle to dark mode manager, then match the
        // native OS title bar to the new theme (Windows only; no-op elsewhere).
        sidebar.setOnThemeChange(() -> {
            darkModeManager.toggleDarkMode();
            dk.dtu.ui.WindowChrome.applyDarkTitleBar(stage, darkModeManager.isDarkMode());
        });

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

        // Drop any pooled connections so they reopen against the current server
        // (handles connecting to a different server) and refresh cached data.
        dk.dtu.methods.Spaces.reset();
        dk.dtu.methods.Users.invalidateUserCache();

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

        // Outer wrapper so the update banner can sit pinned above every scene.
        // Re-applied on each navigation because scenes are rebuilt per view.
        BorderPane outer = new BorderPane();
        outer.setCenter(root);
        if (updateBanner != null) {
            outer.setTop(updateBanner);
        }

        // Use content scene dimensions or fallback to reasonable defaults.
        // Important: add sidebar width so the center content doesn't overlap/cover it.
        double sidebarWidth = sidebar.getPrefWidth() > 0 ? sidebar.getPrefWidth() : 70;
        double contentWidth = contentScene.getWidth() > 0 ? contentScene.getWidth() : 900;
        double width = contentWidth + sidebarWidth;
        double height = contentScene.getHeight() > 0 ? contentScene.getHeight() : 600;

        // Create new scene with the outer wrapper
        Scene sceneWithSidebar = new Scene(outer, width, height);

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

        // The HWND lookup keys on the stage title, which just changed, so
        // re-apply the themed title bar to keep it correct across navigation.
        dk.dtu.ui.WindowChrome.applyDarkTitleBar(stage, darkModeManager.isDarkMode());
    }

    private String buildWindowTitle(String viewTitle) {
        String mainUsers = MainUserConfig.formatMainUsersForTitle();

        if (viewTitle == null || viewTitle.isBlank()) {
            return mainUsers.isBlank() ? APP_TITLE : (APP_TITLE + " - " + mainUsers);
        }
        if (mainUsers.isBlank()) {
            return viewTitle + " - " + APP_TITLE;
        }
        return viewTitle + " - " + APP_TITLE + " - " + mainUsers;
    }

    public void refreshWindowTitle() {
        if (currentState == null) {
            stage.setTitle(buildWindowTitle(null));
            return;
        }

        String viewTitle = switch (currentState.type) {
            case WELCOME -> null;
            case LOGIN -> "Login";
            case MAIN_MENU -> "Main Menu";
            case TODO_LIST -> {
                String listName = currentState.listName;
                yield (listName == null || listName.isBlank()) ? "Todo List" : ("Todo List - " + listName);
            }
        };

        stage.setTitle(buildWindowTitle(viewTitle));
    }

    // ------------------------------------------------------------------
    // In-app updater
    // ------------------------------------------------------------------

    /**
     * Kicks off a one-shot background check for a newer release. When one is
     * found, a dismissible banner is shown at the top of the window. Safe to
     * call more than once; only the first call does any work. Never blocks the
     * UI and shows nothing on failure or when already current.
     */
    public void checkForUpdatesOnLaunch() {
        if (updateCheckStarted) {
            return;
        }
        updateCheckStarted = true;

        Thread worker = new Thread(() -> {
            Optional<ReleaseInfo> release = new UpdateChecker().findNewerRelease();
            release.ifPresent(info -> Platform.runLater(() -> showUpdateBanner(info)));
        }, "update-check");
        worker.setDaemon(true);
        worker.start();
    }

    private void showUpdateBanner(ReleaseInfo info) {
        Label message = new Label("Update available: v" + info.version());
        message.getStyleClass().add(Styles.TITLE_4);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button updateNow = new Button("Update now");
        updateNow.getStyleClass().add(Styles.ACCENT);
        Button releaseNotes = new Button("Release notes");
        releaseNotes.getStyleClass().add(Styles.BUTTON_OUTLINED);
        Button dismiss = new Button("x");
        dismiss.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);

        releaseNotes.setOnAction(e -> UpdateFlow.openReleasesPage(info.releasePageUrl()));
        dismiss.setOnAction(e -> dismissUpdateBanner());

        if (info.hasInstallableAsset()) {
            updateNow.setOnAction(e -> {
                updateNow.setDisable(true);
                updateNow.setText("Downloading...");
                UpdateFlow.downloadAndInstall(info, () -> {
                    updateNow.setDisable(false);
                    updateNow.setText("Update now");
                });
            });
        } else {
            // No auto-installable asset for this platform: offer the page instead.
            updateNow.setText("Open releases page");
            updateNow.setOnAction(e -> UpdateFlow.openReleasesPage(info.releasePageUrl()));
        }

        HBox banner = new HBox(12, message, spacer, updateNow, releaseNotes, dismiss);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setPadding(new Insets(10, 14, 10, 14));
        banner.getStyleClass().addAll("update-banner", Styles.BG_ACCENT_SUBTLE);

        this.updateBanner = banner;

        // Attach immediately to the live scene without a full re-render.
        Scene scene = stage.getScene();
        if (scene != null && scene.getRoot() instanceof BorderPane outer) {
            outer.setTop(banner);
        }
    }

    private void dismissUpdateBanner() {
        this.updateBanner = null;
        Scene scene = stage.getScene();
        if (scene != null && scene.getRoot() instanceof BorderPane outer) {
            outer.setTop(null);
        }
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
        setScene(scene, buildWindowTitle(null));
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
        setScene(scene, buildWindowTitle("Login"));
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
        setScene(scene, buildWindowTitle("Main Menu"));
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
        setScene(scene, buildWindowTitle(listName == null ? "Todo List" : ("Todo List - " + listName)));
    }

    public void setCurrentUser(String username) {
        this.currentUser = username;
    }

    public String getCurrentUser() {
        return currentUser;
    }
}