package dk.dtu.methods;

import dk.dtu.net.ApiSession;
import dk.dtu.net.ShareDto;
import javafx.application.Platform;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Service for a list's public share links (issue #52), backed by the shared
 * HTTP API. Mirrors {@link Counters}'s style: background thread plus
 * {@link Platform#runLater}, blocking HTTP off the FX thread, errors routed
 * through {@link ApiSession#reportError(Throwable)}.
 *
 * <p>Unlike the counters, the share dialog is a single self-contained
 * management panel (list + create + revoke, all in one place) rather than a
 * plain input dialog whose caller performs the network call, so the create
 * and revoke actions below are async with their own callbacks too, instead
 * of throwing synchronously for the caller to wrap in a thread. Every one of
 * these also hands the failure back to its caller via an {@code onError}
 * callback (in addition to the usual {@code reportError} routing), because
 * the dialog needs to tell a 404 (the API has not deployed sharing yet) apart
 * from any other failure.
 */
public final class Shares {

    private Shares() {
    }

    /** Loads the active share links for a list async, ordered as the API returns them. */
    public static void loadShares(String listId, Consumer<List<ShareDto>> onLoaded, Consumer<Exception> onError) {
        new Thread(() -> {
            try {
                List<ShareDto> shares = ApiSession.get().client().getShares(listId);
                Platform.runLater(() -> {
                    if (onLoaded != null) {
                        onLoaded.accept(shares != null ? shares : List.of());
                    }
                });
            } catch (Exception ex) {
                ApiSession.get().reportError(ex);
                Platform.runLater(() -> {
                    if (onError != null) {
                        onError.accept(ex);
                    } else if (onLoaded != null) {
                        onLoaded.accept(List.of());
                    }
                });
            }
        }, "load-shares").start();
    }

    /** Creates a share link async. {@code label} may be null/blank (the API defaults it). */
    public static void createShare(String listId, String label, Consumer<ShareDto> onCreated,
            Consumer<Exception> onError) {
        new Thread(() -> {
            try {
                String trimmed = (label == null || label.isBlank()) ? null : label.trim();
                ShareDto created = ApiSession.get().client().createShare(listId, trimmed);
                Platform.runLater(() -> {
                    if (onCreated != null) {
                        onCreated.accept(created);
                    }
                });
            } catch (Exception ex) {
                ApiSession.get().reportError(ex);
                Platform.runLater(() -> {
                    if (onError != null) {
                        onError.accept(ex);
                    }
                });
            }
        }, "create-share").start();
    }

    /** Revokes a share link async. */
    public static void revokeShare(String listId, String shareId, Runnable onRevoked, Consumer<Exception> onError) {
        new Thread(() -> {
            try {
                ApiSession.get().client().revokeShare(listId, shareId);
                Platform.runLater(() -> {
                    if (onRevoked != null) {
                        onRevoked.run();
                    }
                });
            } catch (Exception ex) {
                ApiSession.get().reportError(ex);
                Platform.runLater(() -> {
                    if (onError != null) {
                        onError.accept(ex);
                    }
                });
            }
        }, "revoke-share").start();
    }

    // -- pure helpers ------------------------------------------------------
    //
    // No JavaFX types in these signatures, so they are unit-testable without
    // instantiating JavaFX. Their behaviour is duplicated VERBATIM in the web
    // edition at website/src/lib/todo/share.ts in the patr7257/PatrickRobelWeb
    // repo: keep the two in lockstep, do not improvise a change on one side
    // only.

    /** Falls back to "Untitled link" for a null or blank (after trim) label. */
    public static String labelOrDefault(String label) {
        if (label == null || label.trim().isEmpty()) {
            return "Untitled link";
        }
        return label.trim();
    }

    /**
     * Builds a share URL from a base origin and a token, stripping every
     * trailing slash from the base first so the result never double-slashes.
     * This is a fallback only: prefer the API's own {@code url} field, which
     * is what the UI actually displays.
     */
    public static String shareUrl(String base, String token) {
        String b = base == null ? "" : base;
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        return b + "/s/" + token;
    }

    /** Human-readable "last opened" summary for a share link. */
    public static String describeLastViewed(Instant lastViewed, int viewCount, Instant now) {
        if (viewCount <= 0 || lastViewed == null) {
            return "Never opened";
        }
        if (viewCount == 1) {
            return "Opened once, " + relative(lastViewed, now);
        }
        return "Opened " + viewCount + " times, last " + relative(lastViewed, now);
    }

    /** Relative "N units ago" phrasing, clamped so a clock-skewed future instant never reads as negative. */
    public static String relative(Instant t, Instant now) {
        long seconds = Duration.between(t, now).getSeconds();
        if (seconds < 0) {
            seconds = 0;
        }
        if (seconds < 60) {
            return "just now";
        }
        if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes == 1 ? "1 minute ago" : minutes + " minutes ago";
        }
        if (seconds < 86400) {
            long hours = seconds / 3600;
            return hours == 1 ? "1 hour ago" : hours + " hours ago";
        }
        long days = seconds / 86400;
        return days == 1 ? "1 day ago" : days + " days ago";
    }

    /** Defensive copy helper for callers that want a mutable snapshot. */
    public static List<ShareDto> copy(List<ShareDto> shares) {
        return shares == null ? new ArrayList<>() : new ArrayList<>(shares);
    }

    // -- 404 handling per share-management route ----------------------------
    //
    // A 404 means something different on each of the three routes, so the
    // mapping is per operation, not a single global "server needs updating"
    // message. Pinned here (rather than in ShareDialog) so it is unit
    // testable without JavaFX.

    /** Identifies the GET .../shares (list load) route for {@link #messageForManagementFailure}. */
    public static final String OP_LOAD = "load";
    /** Identifies the POST .../shares (create) route for {@link #messageForManagementFailure}. */
    public static final String OP_CREATE = "create";
    /** Identifies the DELETE .../shares/{id} (revoke) route for {@link #messageForManagementFailure}. */
    public static final String OP_REVOKE = "revoke";

    /**
     * The message the dialog should show for a failed share-management call,
     * or null when this operation/status combination needs no visible
     * message at all.
     *
     * <ul>
     *   <li>{@link #OP_LOAD} + 404: a valid list id always has a shares
     *       collection (even an empty one), so a 404 here genuinely means an
     *       old server with no share routes at all. This is the only place
     *       the "server needs updating" message belongs, and the dialog hits
     *       it immediately on open, so an old server is caught right away.</li>
     *   <li>{@link #OP_CREATE} + 404: the list id is unknown, most likely
     *       because the list was deleted while the dialog was open.</li>
     *   <li>{@link #OP_REVOKE} + 404: see {@link #isAlreadyGone}, this is a
     *       silent success, not a message.</li>
     *   <li>Any non-404 status: null, the caller falls back to its own
     *       generic handling (the underlying exception was already routed to
     *       {@link ApiSession#reportError(Throwable)} by the async methods
     *       above).</li>
     * </ul>
     */
    public static String messageForManagementFailure(String operation, int status) {
        if (status != 404) {
            return null;
        }
        if (OP_LOAD.equals(operation)) {
            return "Sharing isn't available yet - the server needs updating.";
        }
        if (OP_CREATE.equals(operation)) {
            return "That list no longer exists.";
        }
        return null; // OP_REVOKE (and anything unrecognized): see isAlreadyGone.
    }

    /**
     * True when a 404 on {@link #OP_REVOKE} means the link is already gone,
     * which IS the outcome the user asked for (they revoked it elsewhere, or
     * double clicked): no message, just reload so the row disappears. Revoke
     * is the only route where a 404 is a silent success; if the whole
     * sharing feature were missing on an old server, the initial {@link
     * #OP_LOAD} call would already have failed before revoke could ever run.
     */
    public static boolean isAlreadyGone(String operation, int status) {
        return OP_REVOKE.equals(operation) && status == 404;
    }
}
