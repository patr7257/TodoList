package dk.dtu.scenes;

import atlantafx.base.theme.Styles;
import dk.dtu.ClientConnectDialog;
import dk.dtu.SceneNavigator;
import dk.dtu.ServerPrefs;
import dk.dtu.net.ApiSession;
import dk.dtu.net.TodoApiClient;
import dk.dtu.shared.Config;
import dk.dtu.ui.Icons;
import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.stage.Stage;
import javafx.util.Duration;

public class A_WelcomeScreen {
	private final SceneNavigator navigator;
	private Label connectionStatus;
	private Button loginButton;
	private Button connectButton;

	public A_WelcomeScreen(SceneNavigator navigator) {
		this.navigator = navigator;
	}

	public Scene createScene() {

    // Logo: violet brand mark (themed vector icon)
    FontIcon logo = Icons.checklist(96);
    logo.getStyleClass().add("brand-logo");

	Label title = new Label("TodoList Management System");
    title.getStyleClass().add("welcome-title");
    title.setWrapText(true);
    title.setMaxWidth(560);
    title.setAlignment(Pos.CENTER);
    title.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

    Label tagline = new Label("Lets remember all of our ideas, yay!");
    tagline.getStyleClass().add("welcome-tagline");

    // Connection status label
    connectionStatus = new Label("Checking server...");
    connectionStatus.getStyleClass().addAll("connection-status", "status-idle");

    Label connectionNote = new Label("Sign in with your email and password to continue.");
    connectionNote.getStyleClass().add("welcome-note");

    // Change/choose API server button
    connectButton = new Button("Change Server");
    connectButton.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.LARGE);
    connectButton.setMinWidth(200);
    connectButton.setOnAction(e -> handleConnect());

    // Sign in button (always enabled; login validates against the API)
    loginButton = new Button("Sign in");
    loginButton.setDefaultButton(true);
    loginButton.getStyleClass().addAll(Styles.ACCENT, Styles.LARGE);
    loginButton.setMinWidth(200);
    loginButton.setOnAction(e -> navigator.showLogin());

    VBox headerBox = new VBox(5, logo, title);
    headerBox.setAlignment(Pos.CENTER);

    VBox column = new VBox(15, headerBox, connectionStatus, connectionNote, loginButton, connectButton, tagline);
    column.setAlignment(Pos.CENTER);
    column.setFillWidth(false);
    column.setMaxWidth(560);

    StackPane root = new StackPane(column);
    StackPane.setAlignment(column, Pos.CENTER);
    root.setAlignment(Pos.CENTER);
    root.getStyleClass().add("welcome-screen");
	root.setPadding(new Insets(10, 0, 0, 0));

    Scene scene = new Scene(root, 900, 600);

    FadeTransition ft = new FadeTransition(Duration.millis(600), root);
    ft.setFromValue(0);
    ft.setToValue(1);
    ft.play();

    // Best-effort reachability probe (status only; does not block sign in).
    probeServer();

    return scene;
}

	// Background reachability probe against the configured API base URL. Purely
	// informational: it never disables Sign in, since login itself validates the
	// connection and credentials against the API.
	private void probeServer() {
		String baseUrl = Config.getApiBaseUrl();
		connectionStatus.setText("Checking " + baseUrl + "...");
		setStatusClass("status-connecting");

		new Thread(() -> {
			boolean reachable = new TodoApiClient(baseUrl, null).ping();
			javafx.application.Platform.runLater(() -> {
				if (reachable) {
					connectionStatus.setText("Server: " + baseUrl);
					setStatusClass("status-connected");
				} else {
					connectionStatus.setText("Server not reachable: " + baseUrl + " (you can still try signing in)");
					setStatusClass("status-idle");
				}
			});
		}, "api-probe").start();
	}

	// Swap the single active semantic status class on the connection label
	private void setStatusClass(String statusClass) {
		connectionStatus.getStyleClass().removeAll("status-idle", "status-connecting", "status-connected", "status-error");
		connectionStatus.getStyleClass().add(statusClass);
	}

	private void handleConnect() {
		Stage ownerStage = (Stage) connectButton.getScene().getWindow();

		ClientConnectDialog.ApiSettings settings = ClientConnectDialog.show(ownerStage);
		if (settings != null) {
			// Point Config (system property) and the persisted prefs at the new URL,
			// then rebuild the API client so subsequent calls hit it.
			System.setProperty("todolist.api.url", settings.baseUrl());
			ServerPrefs.saveApiBaseUrl(settings.baseUrl());
			ApiSession.get().configure(ApiSession.get().token());
			probeServer();
		}
	}
}
