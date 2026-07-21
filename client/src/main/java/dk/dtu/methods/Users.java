package dk.dtu.methods;

import dk.dtu.MainUserConfig;
import dk.dtu.net.ApiSession;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * Service for user operations. The closed set of users is owned by the API and
 * returned by GET /state; the desktop app reads it (for the owner/assignee
 * dropdowns) but cannot create or delete users.
 */
public class Users {

    private Users() {}

    // In-memory cache of the user NAME list, shared by every owner dropdown so a
    // view refresh does ONE state fetch instead of one per row. Invalidated on
    // each view refresh so it stays in sync with the server.
    private static volatile List<String> userCache;

    /** Returns the user-name list, fetching state and caching it on first use. */
    public static List<String> getUsersCached(String usersUri) throws Exception {
        List<String> cached = userCache;
        if (cached != null) {
            return cached;
        }
        synchronized (Users.class) {
            if (userCache != null) {
                return userCache;
            }
            // Fetch refreshes ApiSession's name<->id maps used for assignee writes.
            ApiSession.get().fetchState();
            List<String> names = ApiSession.get().userNames();
            userCache = names;
            return names;
        }
    }

    /** Drop the cached user list so the next dropdown load re-fetches from the API. */
    public static void invalidateUserCache() {
        userCache = null;
    }

    // Load all users into a ComboBox
    public static void loadUsersIntoComboBox(ComboBox<String> usersComboBox, String usersUri) {
        loadUsersIntoComboBox(usersComboBox, usersUri, false);
    }

    // Load all users into a ComboBox, optionally including an "All" option.
    public static void loadUsersIntoComboBox(ComboBox<String> usersComboBox, String usersUri, boolean includeAllOption) {
        new Thread(() -> {
            try {
                // Cached: the whole user list is fetched once per refresh, not once
                // per dropdown/row.
                final List<String> tuples = getUsersCached(usersUri);

                Platform.runLater(() -> {
                    String previousValue = usersComboBox.getValue();
                    usersComboBox.getItems().clear();

                    if (includeAllOption) {
                        usersComboBox.getItems().add("All");
                    }

                    for (String username : tuples) {
                        // Add star to main users for easy identification
                        if (MainUserConfig.isMainUser(username)) {
                            usersComboBox.getItems().add(username + " *");
                        } else {
                            usersComboBox.getItems().add(username);
                        }
                    }

                    // Handle restoring previous value (need to account for star)
                    if (previousValue != null) {
                        if (usersComboBox.getItems().contains(previousValue)) {
                            usersComboBox.setValue(previousValue);
                            return;
                        } else {
                            String withStar = previousValue + " *";
                            String withoutStar = previousValue.replace(" *", "");
                            if (usersComboBox.getItems().contains(withStar)) {
                                usersComboBox.setValue(withStar);
                                return;
                            } else if (usersComboBox.getItems().contains(withoutStar)) {
                                usersComboBox.setValue(withoutStar);
                                return;
                            }
                        }
                    }

                    if (includeAllOption) {
                        usersComboBox.setValue("All");
                    } else if (!usersComboBox.getItems().isEmpty() && usersComboBox.getValue() == null) {
                        usersComboBox.setValue(usersComboBox.getItems().getFirst());
                    }
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                ApiSession.get().reportError(ex);
            }
        }, "load-users").start();
    }

    /**
     * Creating users from the desktop app is not supported: the API owns the
     * user set. Reports the situation via the error callback.
     */
    public static void createNewUser(String username, String usersUri,
                                     Consumer<String> onSuccessMessage, Consumer<String> onError) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        String msg = "User accounts are managed by the API; new users cannot be created from the desktop app.";
        if (onError != null) {
            Platform.runLater(() -> onError.accept(msg));
        }
    }

    /** Deleting users from the desktop app is not supported (API owns users). */
    public static void deleteUser(String requestsUri, String responsesUri, String username) throws Exception {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        throw new UnsupportedOperationException(
                "User accounts are managed by the API; users cannot be deleted from the desktop app.");
    }
}
