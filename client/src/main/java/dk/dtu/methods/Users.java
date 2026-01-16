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
        new Thread(() -> {
            try {
                RemoteSpace users = new RemoteSpace(usersUri);
                List<Object[]> tuples = users.queryAll(new FormalField(String.class));

                Platform.runLater(() -> {
                    usersComboBox.getItems().clear();
                    for (Object[] t : tuples) {
                        usersComboBox.getItems().add((String) t[0]);
                    }
                    if (!usersComboBox.getItems().isEmpty() && usersComboBox.getValue() == null) {
                        usersComboBox.setValue(usersComboBox.getItems().get(0));
                    }
                });

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }, "load-users").start();
    }
}
