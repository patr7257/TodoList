package dk.dtu.scenes;

import dk.dtu.ClientConnectDialog;
import dk.dtu.SceneNavigator;
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

    Label title = new Label("Patrick & Elines Amazing Huske-System");
    title.getStyleClass().add("welcome-title");

    Label tagline = new Label("Lets remember all of our ideas, yay!");
    tagline.getStyleClass().add("welcome-tagline");

    // Connection status label
    connectionStatus = new Label("Not connected to server");
    connectionStatus.getStyleClass().add("connection-status");
    connectionStatus.setStyle("-fx-text-fill: #e53935; -fx-font-size: 16px; -fx-font-weight: 500;");
    
    Label connectionNote = new Label("You must connect to the server before signing in.");
    connectionNote.setStyle("-fx-text-fill: #666666; -fx-font-size: 12px; -fx-font-style: italic;");

    // Connect to Server button
    connectButton = new Button("Connect to Server");
    connectButton.getStyleClass().add("primary-button");
    connectButton.setOnAction(e -> handleConnect());

    // Sign in button (disabled until connected)
    loginButton = new Button("Sign in");
    loginButton.setDefaultButton(true);
    loginButton.getStyleClass().add("primary-button");
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
    return scene;
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
			connectionStatus.setStyle("-fx-text-fill: #f39c12; -fx-font-size: 16px; -fx-font-weight: 500;");
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
						connectionStatus.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 16px; -fx-font-weight: 500;");
						loginButton.setDisable(false);
						connectButton.setDisable(false);
						connectButton.setText("Change Server");
						
						// Start notification listener for this connection
						navigator.connectToServer();
					});
				} catch (Exception e) {
					// Connection failed
					javafx.application.Platform.runLater(() -> {
						isConnected = false;
						connectionStatus.setText("Connection failed: " + e.getMessage());
						connectionStatus.setStyle("-fx-text-fill: #e53935; -fx-font-size: 16px; -fx-font-weight: 500;");
						loginButton.setDisable(true);
						connectButton.setDisable(false);
					});
				}
			}, "connection-test").start();
		}
	}
}