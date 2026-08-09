package dk.dtu;

import dk.dtu.methods.Shares;
import dk.dtu.net.ApiException;
import dk.dtu.net.ShareDto;
import dk.dtu.ui.Icons;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Manage a list's public share links (issue #52): the active links with
 * per-row Copy/Revoke actions, and a "Create share link" action that copies
 * the new link to the clipboard automatically. Sits next to
 * {@link CounterDialog} and follows the same shape (a plain {@link Dialog}
 * always prepared via {@link DarkModeManager#prepareDialog}), but unlike
 * CounterDialog (a single-result input form whose caller performs the
 * network call) this dialog owns its own loading and mutations directly,
 * because it is a small self-contained management panel rather than a form.
 *
 * <p>This client NEVER builds a share URL itself: every link shown is the
 * {@code url} field the API returned, so the desktop and web editions are
 * structurally incapable of disagreeing about a link.
 */
public class ShareDialog extends Dialog<Void> {

    private static final String WARNING_TEXT =
            "Anyone with this link can see every task in this list, including task descriptions. "
                    + "They cannot change anything.";
    private static final String EMPTY_TEXT = "This list isn't shared yet.";
    private static final String REVOKE_CONFIRM_TEXT =
            "Anyone using this link will stop being able to see the list. This can't be undone.";

    private static final DateTimeFormatter CREATED_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    private final Window owner;
    private final String listId;

    private final VBox linksBox = new VBox(10);
    private final Label statusLabel = new Label();
    private final TextField labelField = new TextField();
    private final Button createButton = new Button("Create share link");

    public ShareDialog(Window owner, String listId, String listName) {
        this.owner = owner;
        this.listId = listId;

        setTitle("Share list");
        setHeaderText("Share \"" + listName + "\"");
        DarkModeManager.prepareDialog(this, owner);

        Label warning = new Label(WARNING_TEXT);
        warning.setWrapText(true);
        warning.getStyleClass().add("share-warning");

        linksBox.setFillWidth(true);

        labelField.setPromptText("sent to mum");
        HBox.setHgrow(labelField, Priority.ALWAYS);

        createButton.setGraphic(Icons.share());
        createButton.setOnAction(e -> onCreate());

        HBox createRow = new HBox(8, labelField, createButton);
        createRow.setAlignment(Pos.CENTER_LEFT);

        statusLabel.getStyleClass().add("share-status");
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);

        VBox content = new VBox(14, warning, linksBox, createRow, statusLabel);
        content.setPadding(new Insets(18));
        content.setPrefWidth(460);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(360);

        getDialogPane().setContent(scroll);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        reload();
    }

    private void reload() {
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
        linksBox.getChildren().setAll(new Label("Loading..."));
        Shares.loadShares(listId, this::renderShares, this::handleLoadError);
    }

    private void renderShares(List<ShareDto> shares) {
        linksBox.getChildren().clear();
        if (shares == null || shares.isEmpty()) {
            linksBox.getChildren().add(new Label(EMPTY_TEXT));
            return;
        }
        Instant now = Instant.now();
        for (ShareDto share : shares) {
            linksBox.getChildren().add(buildRow(share, now));
        }
    }

    private Node buildRow(ShareDto share, Instant now) {
        Label labelLabel = new Label(Shares.labelOrDefault(share.label()));
        labelLabel.getStyleClass().add("share-row-label");

        Label meta = new Label(createdMeta(share) + " • " + describeLastViewed(share, now));
        meta.getStyleClass().add("share-row-meta");
        meta.setWrapText(true);

        Button copyButton = new Button("Copy");
        copyButton.setGraphic(Icons.copy());
        copyButton.setOnAction(e -> copyToClipboard(share.url()));

        Button revokeButton = new Button("Revoke");
        revokeButton.setOnAction(e -> confirmRevoke(share));

        HBox actions = new HBox(8, copyButton, revokeButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox textCol = new VBox(2, labelLabel, meta);
        HBox.setHgrow(textCol, Priority.ALWAYS);

        HBox row = new HBox(12, textCol, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("share-row");
        return row;
    }

    private String createdMeta(ShareDto share) {
        Instant created = parseInstant(share.createdAt());
        return created == null ? "Created unknown" : "Created " + CREATED_FORMAT.format(created);
    }

    private String describeLastViewed(ShareDto share, Instant now) {
        Instant lastViewed = parseInstant(share.lastViewedAt());
        return Shares.describeLastViewed(lastViewed, share.viewCount(), now);
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private void onCreate() {
        String label = labelField.getText();
        createButton.setDisable(true);
        Shares.createShare(listId, label, created -> {
            createButton.setDisable(false);
            labelField.clear();
            if (created != null && created.url() != null) {
                copyToClipboard(created.url());
                showStatus("Link copied");
            }
            reload();
        }, ex -> {
            createButton.setDisable(false);
            handleCreateError(ex);
        });
    }

    private void confirmRevoke(ShareDto share) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        DarkModeManager.prepareDialog(alert, owner);
        alert.setTitle("Revoke share link");
        alert.setHeaderText("Revoke \"" + Shares.labelOrDefault(share.label()) + "\"?");
        alert.setContentText(REVOKE_CONFIRM_TEXT);
        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                Shares.revokeShare(listId, share.id(), this::reload, this::handleRevokeError);
            }
        });
    }

    private void copyToClipboard(String url) {
        if (url == null) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(url);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void showStatus(String text) {
        statusLabel.setText(text);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }

    /**
     * A 404 on the initial load genuinely means an old server with no share
     * routes at all (see {@link Shares#messageForManagementFailure}): show
     * the deliberate "server needs updating" message instead of a stack
     * trace, and stop offering create since it would only 404 too. Any other
     * failure is already routed to {@link dk.dtu.net.ApiSession#reportError}
     * by {@link Shares}, so this just keeps the panel from claiming an empty
     * share list is the reason nothing loaded.
     */
    private void handleLoadError(Exception ex) {
        if (ex instanceof ApiException api) {
            String message = Shares.messageForManagementFailure(Shares.OP_LOAD, api.status());
            if (message != null) {
                linksBox.getChildren().setAll(new Label(message));
                createButton.setDisable(true);
                return;
            }
        }
        ex.printStackTrace();
        linksBox.getChildren().setAll(new Label(EMPTY_TEXT));
    }

    /**
     * A 404 on create means the list itself is gone (deleted while this
     * dialog was open), not a missing route: the initial load already would
     * have failed if sharing itself were unavailable. Say so calmly and
     * close, rather than leaving the user creating links for a list that no
     * longer exists.
     */
    private void handleCreateError(Exception ex) {
        if (ex instanceof ApiException api) {
            String message = Shares.messageForManagementFailure(Shares.OP_CREATE, api.status());
            if (message != null) {
                showListGoneAndClose(message);
                return;
            }
        }
        ex.printStackTrace();
    }

    /**
     * A 404 on revoke means the link is already gone, which IS the outcome
     * the user asked for (revoked elsewhere, or double clicked): treat it as
     * success and reload so the row disappears, instead of a scary message.
     */
    private void handleRevokeError(Exception ex) {
        if (ex instanceof ApiException api && Shares.isAlreadyGone(Shares.OP_REVOKE, api.status())) {
            reload();
            return;
        }
        ex.printStackTrace();
    }

    private void showListGoneAndClose(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        DarkModeManager.prepareDialog(alert, owner);
        alert.setTitle("List unavailable");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
        close();
    }
}
