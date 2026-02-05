package dk.dtu.methods;

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

    public static void autoLoginUser(
            String username,
            String usersUri,
            Consumer<String> onSuccessMessage) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }

        new Thread(() -> {
            try {
                RemoteSpace users = new RemoteSpace(usersUri);
                Object[] match = users.queryp(new ActualField(username));

                boolean isNewUser = (match == null);
                if (isNewUser) {
                    users.put(username);
                }

                String msg = isNewUser
                        ? "New user " + username + " created, logged in"
                        : "Logged in as " + username;

                System.out.println(msg);
                
                // Notify server of user login
                try {
                    RemoteSpace requests = new RemoteSpace(usersUri.replace(TupleSpaces.USERS, TupleSpaces.REQUESTS));
                    requests.put(TupleSpaces.CMD_USER_LOGIN,
                            java.util.UUID.randomUUID().toString(),
                            username, "", "", "");
                } catch (Exception ignored) {}
                
                Platform.runLater(() -> onSuccessMessage.accept(msg));

            } catch (Exception ex) {
                ex.printStackTrace();
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
                RemoteSpace users = new RemoteSpace(usersUri);
                List<Object[]> tuples = users.queryAll(new FormalField(String.class));

                Platform.runLater(() -> {
                    String previousValue = usersComboBox.getValue();
                    usersComboBox.getItems().clear();

                    if (includeAllOption) {
                        usersComboBox.getItems().add("All");
                    }
                    for (Object[] t : tuples) {
                        usersComboBox.getItems().add((String) t[0]);
                    }

                    if (previousValue != null && usersComboBox.getItems().contains(previousValue)) {
                        usersComboBox.setValue(previousValue);
                        return;
                    }

                    if (includeAllOption) {
                        usersComboBox.setValue("All");
                    } else if (!usersComboBox.getItems().isEmpty() && usersComboBox.getValue() == null) {
                        usersComboBox.setValue(usersComboBox.getItems().getFirst());
                    }
                });

            } catch (Exception ex) {
                ex.printStackTrace();
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
                RemoteSpace users = new RemoteSpace(usersUri);
                Object[] match = users.queryp(new ActualField(username));

                if (match == null) {
                    System.err.println("User " + username + " does not exist");
                    return;
                }

                String msg = "Logged in as " + username;
                System.out.println(msg);
                
                // Notify server of user login
                try {
                    RemoteSpace requests = new RemoteSpace(usersUri.replace(TupleSpaces.USERS, TupleSpaces.REQUESTS));
                    requests.put(TupleSpaces.CMD_USER_LOGIN,
                            java.util.UUID.randomUUID().toString(),
                            username, "", "", "");
                } catch (Exception ignored) {}
                
                Platform.runLater(() -> onSuccessMessage.accept(msg));

            } catch (Exception ex) {
                ex.printStackTrace();
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
                RemoteSpace users = new RemoteSpace(usersUri);
                Object[] match = users.queryp(new ActualField(username));

                if (match != null) {
                    // User already exists
                    Platform.runLater(() -> onError.accept("Username '" + username + "' already exists. Please choose a different name or login as existing user."));
                    return;
                }

                // Create new user
                users.put(username);
                String msg = "New user '" + username + "' created and logged in";
                System.out.println(msg);
                
                // Notify server of user login
                try {
                    RemoteSpace requests = new RemoteSpace(usersUri.replace(TupleSpaces.USERS, TupleSpaces.REQUESTS));
                    requests.put(TupleSpaces.CMD_USER_LOGIN,
                            java.util.UUID.randomUUID().toString(),
                            username, "", "", "");
                } catch (Exception ignored) {}
                
                Platform.runLater(() -> onSuccessMessage.accept(msg));

            } catch (Exception ex) {
                ex.printStackTrace();
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
}
