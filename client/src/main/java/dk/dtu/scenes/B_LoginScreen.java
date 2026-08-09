package dk.dtu.scenes;

import atlantafx.base.theme.Styles;
import dk.dtu.SceneNavigator;
import dk.dtu.ServerPrefs;
import dk.dtu.auth.BrowserSignIn;
import dk.dtu.auth.Pkce;
import dk.dtu.net.ApiSession;
import dk.dtu.net.WebAuthClient;
import dk.dtu.shared.Config;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/**
 * Browser-mediated sign in (issue #51). The desktop client cannot do passkeys
 * (they are browser and platform mediated), so all credentials live on the
 * website and this screen is a waiting room, not a form:
 *
 * <ol>
 *   <li>Bind a loopback listener on an ephemeral port ({@link BrowserSignIn}).</li>
 *   <li>Open the website's sign-in page in the system browser, passing the port,
 *       the anti-forgery state and the PKCE challenge.</li>
 *   <li>Receive a one-time code on the loopback callback, then exchange it (plus
 *       the PKCE verifier) for a session token over HTTPS
 *       ({@link WebAuthClient}).</li>
 * </ol>
 *
 * <p>The typeable code field is the load-bearing fallback, not decoration: it is
 * the only thing that rescues the flow when the magic link is opened on a phone,
 * when the browser did not open, or when a firewall blocks the loopback
 * listener. It hits the same exchange route with the same verifier.
 *
 * <p>Password sign in still exists in {@link ApiSession#login(String, String)}
 * for older clients; it is simply no longer reachable from this UI.
 */
public class B_LoginScreen {

    private final SceneNavigator navigator;

    // The active attempt. Null only when the loopback listener could not bind,
    // in which case handshake/verifier below still drive the typed-code path.
    private BrowserSignIn signIn;
    private String verifier;
    private String signInUrl;

    // Set when the user pressed Cancel, so the resulting CancellationException
    // is not reported to them as a failure.
    private volatile boolean cancelled;
    // Guards against the loopback callback and the typed code both completing.
    private volatile boolean finished;

    public B_LoginScreen(SceneNavigator navigator) {
        this.navigator = navigator;
    }

    public Scene createScene() {
        String webOrigin = Config.getWebBaseUrl();

        Label title = new Label("Sign in");
        title.getStyleClass().add("login-title");

        Label serverLabel = new Label("Sign in at: " + webOrigin);
        serverLabel.getStyleClass().add("settings-note");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(28, 28);

        Label statusLabel = new Label("Opening your browser...");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(400);

        HBox statusRow = new HBox(12, spinner, statusLabel);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        // Selectable, copyable URL for when the browser did not open by itself.
        TextField urlField = new TextField();
        urlField.setEditable(false);
        urlField.setPrefWidth(400);
        urlField.setMaxWidth(400);
        urlField.getStyleClass().add("settings-note");

        Button copyButton = new Button("Copy link");
        copyButton.getStyleClass().add(Styles.BUTTON_OUTLINED);
        copyButton.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(urlField.getText());
            Clipboard.getSystemClipboard().setContent(content);
            copyButton.setText("Copied");
        });

        HBox urlRow = new HBox(10, urlField, copyButton);
        urlRow.setAlignment(Pos.CENTER_LEFT);

        Label codeHint = new Label("Signed in on your phone, or the browser did not open? "
                + "Type the 8 character code the website shows.");
        codeHint.setWrapText(true);
        codeHint.setMaxWidth(400);
        codeHint.getStyleClass().add("settings-note");

        TextField codeField = new TextField();
        codeField.setPromptText("XXXX-XXXX");
        codeField.setPrefWidth(200);

        Button codeButton = new Button("Sign in with code");
        codeButton.getStyleClass().add(Styles.ACCENT);

        HBox codeRow = new HBox(10, codeField, codeButton);
        codeRow.setAlignment(Pos.CENTER_LEFT);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("status-error");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(400);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add(Styles.BUTTON_OUTLINED);
        cancelButton.setOnAction(e -> {
            cancelled = true;
            if (signIn != null) {
                signIn.cancel();
            }
            navigator.showWelcome();
        });

        Runnable submitCode = () -> submitTypedCode(codeField, codeButton, statusLabel,
                spinner, errorLabel);
        codeButton.setOnAction(e -> submitCode.run());
        codeField.setOnAction(e -> submitCode.run());

        VBox box = new VBox(14, title, serverLabel, statusRow, urlRow,
                new Separator(), codeHint, codeRow, errorLabel, cancelButton);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("login-box");
        box.setMaxWidth(520);

        VBox root = new VBox(box);
        root.setAlignment(Pos.CENTER);
        root.setFillWidth(false);
        root.getStyleClass().add("login-root");
        root.setPadding(new Insets(40));

        startBrowserSignIn(webOrigin, urlField, statusLabel, spinner, errorLabel);

        return new Scene(root, 900, 600);
    }

    // -- the browser half ------------------------------------------------------

    private void startBrowserSignIn(String webOrigin, TextField urlField, Label statusLabel,
                                    ProgressIndicator spinner, Label errorLabel) {
        try {
            // Bind BEFORE the browser opens, so no other local process can claim
            // the port first and impersonate the callback.
            signIn = BrowserSignIn.start(webOrigin);
            verifier = signIn.verifier();
            signInUrl = signIn.signInUrl();
        } catch (Exception ex) {
            // No loopback listener: the browser has nowhere to call back to, so
            // the typed code becomes the only route. Still offer the URL (without
            // a port) so the user can sign in and read the code off the page.
            Pkce.Handshake handshake = Pkce.newHandshake();
            verifier = handshake.verifier();
            signInUrl = BrowserSignIn.signInUrl(webOrigin, null, handshake.state(),
                    handshake.challenge());
            urlField.setText(signInUrl);
            spinner.setVisible(false);
            statusLabel.setText("Open the link below, then type the code the website shows.");
            showError(errorLabel, "Could not listen for the browser callback on this machine. "
                    + "Use the code field below.");
            return;
        }

        urlField.setText(signInUrl);

        boolean opened = signIn.openBrowser();
        statusLabel.setText(opened
                ? "Waiting for you to finish signing in in your browser..."
                : "Could not open your browser. Copy the link below and open it yourself.");

        CompletableFuture<String> code = signIn.code();
        code.whenComplete((value, error) -> {
            if (error != null) {
                Platform.runLater(() -> handleWaitFailure(error, statusLabel, spinner, errorLabel));
                return;
            }
            Platform.runLater(() -> statusLabel.setText("Signing in..."));
            exchangeOffThread(value, statusLabel, spinner, errorLabel);
        });
    }

    private void handleWaitFailure(Throwable error, Label statusLabel, ProgressIndicator spinner,
                                   Label errorLabel) {
        if (cancelled || finished || error instanceof CancellationException) {
            // Either the user pressed Cancel (the welcome screen is already up),
            // or a typed code won the race and cancelled the listener itself.
            return;
        }
        spinner.setVisible(false);
        if (error instanceof TimeoutException) {
            statusLabel.setText("The sign-in window expired.");
            showError(errorLabel, "No answer from the browser within 15 minutes. "
                    + "Go back and start sign in again, or paste a fresh code below.");
            return;
        }
        statusLabel.setText("Sign in did not complete.");
        showError(errorLabel, "Something went wrong while waiting for the browser: "
                + error.getMessage());
    }

    // -- the typed-code half ---------------------------------------------------

    private void submitTypedCode(TextField codeField, Button codeButton, Label statusLabel,
                                 ProgressIndicator spinner, Label errorLabel) {
        String code = Pkce.normalizeCode(codeField.getText());
        if (code.isEmpty()) {
            showError(errorLabel, "Type the code the website showed you.");
            return;
        }
        if (!Pkce.looksLikeCode(code)) {
            showError(errorLabel, "That code contains characters the website never uses. "
                    + "Check it and try again.");
            return;
        }

        hideError(errorLabel);
        codeButton.setDisable(true);
        codeField.setDisable(true);
        spinner.setVisible(true);
        statusLabel.setText("Signing in...");

        exchangeOffThread(code, statusLabel, spinner, errorLabel, () -> {
            codeButton.setDisable(false);
            codeField.setDisable(false);
        });
    }

    // -- the exchange (shared by both halves) ----------------------------------

    private void exchangeOffThread(String code, Label statusLabel, ProgressIndicator spinner,
                                   Label errorLabel) {
        exchangeOffThread(code, statusLabel, spinner, errorLabel, null);
    }

    /**
     * Trades a one-time code for a session token on a background thread (the
     * call is blocking HTTP), then installs the session and routes on. Both the
     * loopback code and the typed code come through here with the same verifier,
     * because the website's exchange route does not care which way the code
     * reached us.
     */
    private void exchangeOffThread(String code, Label statusLabel, ProgressIndicator spinner,
                                   Label errorLabel, Runnable onFailedUi) {
        if (finished) {
            return;
        }
        finished = true;

        String webOrigin = Config.getWebBaseUrl();
        String pkceVerifier = verifier;

        new Thread(() -> {
            try {
                WebAuthClient web = new WebAuthClient(webOrigin);
                WebAuthClient.DesktopExchange res = web.exchange(code, pkceVerifier);

                // The listener has done its job; make sure it is gone either way.
                if (signIn != null) {
                    signIn.cancel();
                }

                ApiSession.get().applyToken(res.token(), res.user());

                String email = res.user() != null ? res.user().email() : null;
                String name = (res.user() != null && res.user().name() != null)
                        ? res.user().name()
                        : (email != null ? email : "User");

                // Persist the session so a relaunch stays signed in.
                ServerPrefs.saveApiBaseUrl(Config.getApiBaseUrl());
                ServerPrefs.saveWebBaseUrl(webOrigin);
                ServerPrefs.saveAuth(res.token(), email);

                Platform.runLater(() -> {
                    spinner.setVisible(false);
                    navigator.setCurrentUser(name);
                    navigator.connectToServer(); // start the state poller
                    // The dashboard is the front page after login (issue #46);
                    // it also clears history so an immediate Back press cannot
                    // return to this screen.
                    navigator.showDashboardAfterLogin();
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                finished = false; // let the user try again with a fresh code
                Platform.runLater(() -> {
                    spinner.setVisible(false);
                    statusLabel.setText("Sign in did not complete.");
                    showError(errorLabel, friendlyError(ex));
                    if (onFailedUi != null) {
                        onFailedUi.run();
                    }
                });
            }
        }, "desktop-exchange").start();
    }

    private static String friendlyError(Exception ex) {
        if (ex instanceof dk.dtu.net.ApiException api) {
            if (api.status() == 400 || api.status() == 401 || api.status() == 410) {
                return "That code is not valid any more. Codes last 5 minutes and work once, "
                        + "so start sign in again to get a fresh one.";
            }
            if (api.status() == 404) {
                return "This website version does not support desktop sign in yet.";
            }
            return "Sign in failed (the website said HTTP " + api.status() + ").";
        }
        return "Could not reach " + Config.getWebBaseUrl()
                + ". Check the web address in Change Server and your connection.";
    }

    private static void showError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private static void hideError(Label errorLabel) {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
