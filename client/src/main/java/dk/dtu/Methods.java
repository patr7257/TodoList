package dk.dtu;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import java.util.function.Consumer;

import org.jspace.ActualField;
import org.jspace.FormalField;
import org.jspace.RemoteSpace;

import java.util.List;
import java.util.UUID;

public class Methods {
	// Prevent instantiation (use static methods only)
	private Methods() {
	}

	// Send ping to server
	// Used to check if server is reachable in MainMenu
	public static void sendPing(Label statusLabel, Button pingButton, String requestsUri) {
		setStatus(statusLabel, "Sending ping...");
		pingButton.setDisable(true);

		new Thread(() -> {
			try {
				RemoteSpace requests = new RemoteSpace(requestsUri);
				String requestId = UUID.randomUUID().toString();

				// Request tuple format: (cmd, requestId, a1, a2, a3, a4)
				requests.put(TupleSpaces.CMD_PING, requestId, "", "", "", "");

				Platform.runLater(() -> {
					setStatus(statusLabel, "Ping sent");
					pingButton.setDisable(false);
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					setStatus(statusLabel, "Ping failed: " + ex.getMessage());
					pingButton.setDisable(false);
				});
			}
		}, "send-ping").start();
	}

	// Load todo lists from server and populate the ListView
	// Used in MainMenu to show available todo lists
	public static void loadTodoLists(Label statusLabel, Button refreshButton, ListView<ListEntry> listsView,
			String todoListsUri) {
		setStatus(statusLabel, "Connecting to server...");
		refreshButton.setDisable(true);

		new Thread(() -> {
			try {
				RemoteSpace todoLists = new RemoteSpace(todoListsUri);

				// Tuple format: (listId:String, listName:String)
				List<Object[]> tuples = todoLists.queryAll(
						new FormalField(String.class),
						new FormalField(String.class));

				Platform.runLater(() -> {
					listsView.getItems().clear();
					for (Object[] t : tuples) {
						listsView.getItems().add(new ListEntry((String) t[0], (String) t[1]));
					}
					setStatus(statusLabel, "Loaded " + tuples.size() + " lists");
					refreshButton.setDisable(false);
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					setStatus(statusLabel, "Failed: " + ex.getMessage());
					refreshButton.setDisable(false);
				});
			}
		}, "load-todo-lists").start();
	}

	// Update status label text
	// Used in various scenes to show status messages
	// Example: "Connecting to server...", "Loaded 5 lists", "Ping failed: ..."
	public static void setStatus(Label statusLabel, String text) {
		statusLabel.setText(text);
	}

	// List entry representing a todo list (id and name)
	// Used in ListView to display available todo lists
	// Example: "l1 - Shopping"
	// TODO: Make sure this works
	public static final class ListEntry {
		public final String id;
		public final String name;

		public ListEntry(String id, String name) {
			this.id = id;
			this.name = name;
		}

		@Override
		public String toString() {
			return id + " - " + name;
		}
	}

	public static void autoLoginUser(
			Label statusLabel,
			Button loginButton,
			String username,
			String usersUri,
			Consumer<String> onSuccessMessage) {
		if (username == null || username.isBlank()) {
			setStatus(statusLabel, "Enter a username");
			return;
		}

		setStatus(statusLabel, "Checking user...");
		loginButton.setDisable(true);

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

				Platform.runLater(() -> {
					onSuccessMessage.accept(msg);
					loginButton.setDisable(false);
				});

			} catch (Exception ex) {
				Platform.runLater(() -> {
					setStatus(statusLabel, "Login failed: " + ex.getMessage());
					loginButton.setDisable(false);
				});
			}
		}, "login-user").start();
	}

}
