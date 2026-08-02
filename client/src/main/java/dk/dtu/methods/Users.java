package dk.dtu.methods;

import dk.dtu.MainUserConfig;
import dk.dtu.net.ApiModels.StateResponse;
import dk.dtu.net.ApiModels.UserRef;
import dk.dtu.net.ApiSession;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Service for user operations. The closed set of users is owned by the API and
 * returned by GET /state; the desktop app reads it (for the owner/assignee
 * dropdowns) but cannot create or delete users.
 */
public class Users {

    private Users() {}

    // In-memory caches shared by every owner dropdown so a view refresh does
    // ONE state fetch instead of one per row. Invalidated on each view refresh
    // so they stay in sync with the server. userRefCache carries the real
    // (id, name) pairs the owner ComboBoxes now bind to and compare by id;
    // userCache is the legacy display-name-only list some callers still use.
    private static volatile List<String> userCache;
    private static volatile List<UserRef> userRefCache;

    /** Returns the user-name list, fetching state and caching it on first use. */
    public static List<String> getUsersCached() throws Exception {
        ensureLoaded();
        return userCache;
    }

    /**
     * Returns the (id, name) user list, fetching state and caching it on first
     * use. The source for any owner/assignee ComboBox that must compare
     * selections by id rather than by display name.
     */
    public static List<UserRef> getUserRefsCached() throws Exception {
        ensureLoaded();
        return userRefCache;
    }

    private static void ensureLoaded() throws Exception {
        if (userCache != null && userRefCache != null) {
            return;
        }
        synchronized (Users.class) {
            if (userCache != null && userRefCache != null) {
                return;
            }
            // Fetch refreshes ApiSession's name<->id maps used for assignee writes.
            StateResponse state = ApiSession.get().fetchState();
            List<String> names = ApiSession.get().userNames();
            List<UserRef> refs = new ArrayList<>();
            if (state != null && state.users() != null) {
                for (UserRef u : state.users()) {
                    if (u != null && u.id() != null) {
                        refs.add(u);
                    }
                }
            }
            userCache = names;
            userRefCache = refs;
        }
    }

    /** Drop the cached user list so the next dropdown load re-fetches from the API. */
    public static void invalidateUserCache() {
        userCache = null;
        userRefCache = null;
    }

    // Load all users into a ComboBox, optionally including an "All" option.
    public static void loadUsersIntoComboBox(ComboBox<String> usersComboBox, boolean includeAllOption) {
        new Thread(() -> {
            try {
                // Cached: the whole user list is fetched once per refresh, not once
                // per dropdown/row.
                final List<String> users = getUsersCached();

                Platform.runLater(() -> {
                    String previousValue = usersComboBox.getValue();
                    usersComboBox.getItems().clear();

                    if (includeAllOption) {
                        usersComboBox.getItems().add("All");
                    }

                    for (String username : users) {
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

    /** Sentinel UserRef standing in for "All" (a filter) / "no owner" (a clear action). */
    public static final UserRef NO_OWNER = new UserRef(null, "All");

    /**
     * Load all users into a ComboBox bound to real {@link UserRef}s (id + name),
     * optionally including the {@link #NO_OWNER} sentinel first. Selections must
     * be compared and written by {@link UserRef#id()}, never by display name.
     */
    public static void loadUserRefsIntoComboBox(ComboBox<UserRef> usersComboBox, boolean includeAllOption) {
        new Thread(() -> {
            try {
                final List<UserRef> users = getUserRefsCached();

                Platform.runLater(() -> {
                    usersComboBox.setCellFactory(lv -> new ListCell<>() {
                        @Override
                        protected void updateItem(UserRef item, boolean empty) {
                            super.updateItem(item, empty);
                            setText(empty || item == null ? "" : displayLabel(item));
                            setAlignment(Pos.CENTER);
                        }
                    });
                    usersComboBox.setButtonCell(new ListCell<>() {
                        @Override
                        protected void updateItem(UserRef item, boolean empty) {
                            super.updateItem(item, empty);
                            setText(empty || item == null ? "" : displayLabel(item));
                            setAlignment(Pos.CENTER);
                        }
                    });

                    UserRef previousValue = usersComboBox.getValue();
                    usersComboBox.getItems().clear();

                    if (includeAllOption) {
                        usersComboBox.getItems().add(NO_OWNER);
                    }
                    usersComboBox.getItems().addAll(users);

                    if (previousValue != null && usersComboBox.getItems().contains(previousValue)) {
                        usersComboBox.setValue(previousValue);
                    } else if (includeAllOption) {
                        usersComboBox.setValue(NO_OWNER);
                    } else if (!usersComboBox.getItems().isEmpty() && usersComboBox.getValue() == null) {
                        usersComboBox.setValue(usersComboBox.getItems().get(0));
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                ApiSession.get().reportError(ex);
            }
        }, "load-user-refs").start();
    }

    /** "Name *" for a main user (presentation only, never round-trips through a write). */
    private static String displayLabel(UserRef user) {
        if (user.id() == null) {
            return user.name();
        }
        return MainUserConfig.isMainUser(user.name()) ? user.name() + " *" : user.name();
    }

    /**
     * Creating users from the desktop app is not supported: the API owns the
     * user set. Reports the situation via the error callback.
     */
    public static void createNewUser(String username,
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
    public static void deleteUser(String username) throws Exception {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        throw new UnsupportedOperationException(
                "User accounts are managed by the API; users cannot be deleted from the desktop app.");
    }
}
