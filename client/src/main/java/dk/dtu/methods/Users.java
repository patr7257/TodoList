package dk.dtu.methods;

import dk.dtu.MainUserConfig;
import dk.dtu.shared.Config;
import dk.dtu.shared.TupleSpaces;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import org.jspace.ActualField;
import org.jspace.FormalField;
import org.jspace.RemoteSpace;

import java.util.List;
import java.util.function.Consumer;

// Service for user operations
public class Users {

    private Users() {}

    // In-memory cache of the user list, shared by every owner dropdown so opening
    // a list does ONE user query instead of one per row. Invalidated on each view
    // refresh and whenever a user is added, so it stays in sync with the server.
    private static volatile List<String> userCache;

    /** Returns the user list, loading and caching it once on first use. */
    public static List<String> getUsersCached(String usersUri) throws Exception {
        List<String> cached = userCache;
        if (cached != null) {
            return cached;
        }
        synchronized (Spaces.IO_LOCK) {
            if (userCache != null) {
                return userCache;
            }
            RemoteSpace users = Spaces.get(usersUri);
            List<Object[]> tuples = users.queryAll(new FormalField(String.class));
            List<String> list = new java.util.ArrayList<>();
            if (tuples != null) {
                for (Object[] t : tuples) {
                    list.add((String) t[0]);
                }
            }
            userCache = list;
            return list;
        }
    }

    /** Drop the cached user list so the next dropdown load re-queries the server. */
    public static void invalidateUserCache() {
        userCache = null;
    }

    public static void autoLoginUser(
            String username,
            String usersUri,
            Consumer<String> onSuccessMessage) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }

        new Thread(() -> {
            try {
                boolean isNewUser;
                synchronized (Spaces.IO_LOCK) {
                    RemoteSpace users = Spaces.get(usersUri);
                    Object[] match = users.queryp(new ActualField(username));
                    isNewUser = (match == null);
                    if (isNewUser) {
                        users.put(username);
                        invalidateUserCache();
                    }
                }

                String msg = isNewUser
                        ? "New user " + username + " created, logged in"
                        : "Logged in as " + username;

                System.out.println(msg);

                notifyServerLogin(usersUri, username);
                
                Platform.runLater(() -> onSuccessMessage.accept(msg));

            } catch (Exception ex) {
                ex.printStackTrace();
                if (Config.isConnectionError(ex)) {
                    Config.handleConnectionError(ex);
                }
            }
        }, "login-user").start();
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
                // per dropdown/row (opening a list built one query per visible row).
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
                            // Check if the previous value was a main user (might need star now)
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
                if (Config.isConnectionError(ex)) {
                    Config.handleConnectionError(ex);
                }
            }
        }, "load-users").start();
    }

    // Login as an existing user (without creating a new one)
    public static void loginExistingUser(
            String username,
            String usersUri,
            Consumer<String> onSuccessMessage) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }

        new Thread(() -> {
            try {
                Object[] match;
                synchronized (Spaces.IO_LOCK) {
                    match = Spaces.get(usersUri).queryp(new ActualField(username));
                }

                if (match == null) {
                    System.err.println("User " + username + " does not exist");
                    return;
                }

                String msg = "Logged in as " + username;
                System.out.println(msg);

                notifyServerLogin(usersUri, username);
                
                Platform.runLater(() -> onSuccessMessage.accept(msg));

            } catch (Exception ex) {
                ex.printStackTrace();
                if (Config.isConnectionError(ex)) {
                    Config.handleConnectionError(ex);
                }
            }
        }, "login-existing-user").start();
    }

    // Create a new user (with validation to prevent duplicates)
    public static void createNewUser(
            String username,
            String usersUri,
            Consumer<String> onSuccessMessage,
            Consumer<String> onError) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }

        new Thread(() -> {
            try {
                Object[] match;
                synchronized (Spaces.IO_LOCK) {
                    match = Spaces.get(usersUri).queryp(new ActualField(username));
                }

                if (match != null) {
                    // User already exists
                    Platform.runLater(() -> onError.accept("Username '" + username + "' already exists. Please choose a different name or login as existing user."));
                    return;
                }

                // Create new user
                synchronized (Spaces.IO_LOCK) {
                    Spaces.get(usersUri).put(username);
                }
                invalidateUserCache();
                String msg = "New user '" + username + "' created successfully";
                System.out.println(msg);
                
                // Note: Not logging in - just creating the user
                
                Platform.runLater(() -> onSuccessMessage.accept(msg));

            } catch (Exception ex) {
                ex.printStackTrace();
                if (Config.isConnectionError(ex)) {
                    Config.handleConnectionError(ex);
                }
                Platform.runLater(() -> onError.accept("Error creating user: " + ex.getMessage()));
            }
        }, "create-new-user").start();
    }

    public static void deleteUser(
            String requestsUri,
            String responsesUri,
            String username) throws Exception {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
                requestsUri,
                responsesUri,
                TupleSpaces.CMD_USER_DELETE,
                username,
                "",
                "",
                "");
    }

    private static void notifyServerLogin(String usersUri, String username) {
        try {
            String requestsUri = usersUri.replace(TupleSpaces.USERS, TupleSpaces.REQUESTS);
            synchronized (Spaces.IO_LOCK) {
                Spaces.get(requestsUri).put(
                        TupleSpaces.CMD_USER_LOGIN,
                        java.util.UUID.randomUUID().toString(),
                        username,
                        "",
                        "",
                        "");
            }
        } catch (Exception ignored) {
            if (Config.isConnectionError(ignored)) {
                Config.handleConnectionError(ignored);
            }
        }
    }
}
