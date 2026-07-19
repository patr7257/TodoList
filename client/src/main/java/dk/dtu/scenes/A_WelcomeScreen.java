package dk.dtu.scenes;

import atlantafx.base.theme.Styles;
import dk.dtu.ClientConnectDialog;
import dk.dtu.SceneNavigator;
import dk.dtu.ServerPrefs;
import dk.dtu.shared.Config;
import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

public class A_WelcomeScreen {
	private final SceneNavigator navigator;
	private boolean isConnected = false;
	private Label connectionStatus;
	private Button loginButton;
	private Button connectButton;

	public A_WelcomeScreen(SceneNavigator navigator) {
		this.navigator = navigator;
	}

	public Scene createScene() {

    // Logo
    ImageView logo = new ImageView(new Image(
            getClass().getResource("/Icons/todo.png").toExternalForm()));
    logo.setFitWidth(70);
    logo.setPreserveRatio(true);

	Label title = new Label("TodoList Management System");
    title.getStyleClass().add("welcome-title");

    Label tagline = new Label("Lets remember all of our ideas, yay!");
    tagline.getStyleClass().add("welcome-tagline");

    // Connection status label
    connectionStatus = new Label("Not connected to server");
    connectionStatus.getStyleClass().addAll("connection-status", "status-idle");

    Label connectionNote = new Label("You must connect to the server before signing in.");
    connectionNote.getStyleClass().add("welcome-note");

    // Connect to Server button
    connectButton = new Button("Connect to Server");
    connectButton.getStyleClass().addAll(Styles.ACCENT, Styles.LARGE);
    connectButton.setMinWidth(200);
    connectButton.setOnAction(e -> handleConnect());

    // Sign in button (disabled until connected)
    loginButton = new Button("Sign in");
    loginButton.setDefaultButton(true);
    loginButton.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.LARGE);
    loginButton.setMinWidth(200);
    loginButton.setDisable(true);
    loginButton.setOnAction(e -> navigator.showLogin());

    // NEW: group logo + title with tight spacing
    VBox headerBox = new VBox(5, logo, title);
    headerBox.setAlignment(Pos.CENTER);

    // Root layout: spacing mainly between header, subtitle, button, tagline
    VBox root = new VBox(15, headerBox, connectionStatus, connectionNote, connectButton, loginButton, tagline);
    root.setAlignment(Pos.CENTER);
    root.getStyleClass().add("welcome-screen");
	root.setPadding(new Insets(10, 0, 0, 0));

    Scene scene = new Scene(root, 900, 600);

    FadeTransition ft = new FadeTransition(Duration.millis(600), root);
    ft.setFromValue(0);
    ft.setToValue(1);
    ft.play();

    // Try the remembered (or default) server automatically, so the user isn't
    // forced to click "Connect to Server" on every launch.
    attemptAutoConnect();

    return scene;
}

	// Attempt a connection to the current effective server (remembered server, or
	// the baked default) as soon as the welcome screen loads. Mirrors the dialog's
	// connection test in handleConnect(), but is driven by Config instead of a
	// dialog result, and stays quiet on failure since this is not a user action.
	private void attemptAutoConnect() {
		String ip = Config.getServerIp();
		int port = Config.getPort();

		connectionStatus.setText("Connecting to " + ip + ":" + port + "...");
		setStatusClass("status-connecting");
		connectButton.setDisable(true);

		new Thread(() -> {
			try {
				org.jspace.RemoteSpace testSpace = new org.jspace.RemoteSpace(Config.getRequestsUri());

				javafx.application.Platform.runLater(() -> {
					isConnected = true;
					connectionStatus.setText("Connected to " + ip + ":" + port);
					setStatusClass("status-connected");
					loginButton.setDisable(false);
					connectButton.setDisable(false);
					connectButton.setText("Change Server");

					// Remember this server since the connection actually succeeded.
					ServerPrefs.save(ip, port);

					navigator.connectToServer();
				});
			} catch (Exception e) {
				javafx.application.Platform.runLater(() -> {
					isConnected = false;
					connectionStatus.setText("Not connected to server");
					setStatusClass("status-idle");
					loginButton.setDisable(true);
					connectButton.setDisable(false);
				});
			}
		}, "auto-connect").start();
	}

	// Swap the single active semantic status class on the connection label
	private void setStatusClass(String statusClass) {
		connectionStatus.getStyleClass().removeAll("status-idle", "status-connecting", "status-connected", "status-error");
		connectionStatus.getStyleClass().add(statusClass);
	}

	private void handleConnect() {
		// Get the stage from the scene
		Stage ownerStage = (Stage) connectButton.getScene().getWindow();
		
		ClientConnectDialog.ConnectionSettings settings = ClientConnectDialog.show(ownerStage);
		if (settings != null) {
			// Update system properties first
			System.setProperty("todolist.server.ip", settings.serverIp());
			System.setProperty("todolist.port", Integer.toString(settings.port()));
			
			// Show connecting status
			connectionStatus.setText("Connecting to " + settings.serverIp() + ":" + settings.port() + "...");
			setStatusClass("status-connecting");
			connectButton.setDisable(true);
			loginButton.setDisable(true);
			
			// Test connection in background thread
			new Thread(() -> {
				try {
					// Try to create a RemoteSpace connection to verify server is reachable
					org.jspace.RemoteSpace testSpace = new org.jspace.RemoteSpace(Config.getRequestsUri());
					
					// Connection successful - start notification listener
					javafx.application.Platform.runLater(() -> {
						isConnected = true;
						connectionStatus.setText("Connected to " + settings.serverIp() + ":" + settings.port());
						setStatusClass("status-connected");
						loginButton.setDisable(false);
						connectButton.setDisable(false);
						connectButton.setText("Change Server");

						// Remember this server since the connection actually succeeded.
						ServerPrefs.save(settings.serverIp(), settings.port());

						// Start notification listener for this connection
						navigator.connectToServer();
					});
				} catch (Exception e) {
					// Connection failed
					javafx.application.Platform.runLater(() -> {
						isConnected = false;
						connectionStatus.setText("Connection failed: " + e.getMessage());
						setStatusClass("status-error");
						loginButton.setDisable(true);
						connectButton.setDisable(false);
					});
				}
			}, "connection-test").start();
		}
	}
}