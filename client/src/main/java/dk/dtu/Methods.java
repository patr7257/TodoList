package dk.dtu;

import dk.dtu.shared.TupleSpaces;
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
	public static void loadTodoLists(Label statusLabel, ListView<ListEntry> listsView,
			String todoListsUri) {
		setStatus(statusLabel, "Connecting to server...");

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
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					setStatus(statusLabel, "Failed: " + ex.getMessage());
				});
			}
		}, "load-todo-lists").start();
	}

	// Update status label text
	// Used in various scenes to show status messages
	// Example: "Connecting to server...", "Loaded 5 lists", "Ping failed: ..."
	public static void setStatus(Label statusLabel, String text) {
		if (statusLabel == null) return;
		if (Platform.isFxApplicationThread()) {
			statusLabel.setText(text);
		} else {
			Platform.runLater(() -> statusLabel.setText(text));
		}
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

	public static final class TaskEntry {
		public final String listId;
		public final String id;
		public final String title;
		public final String owner;
		public final String status;

		public TaskEntry(String listId, String id, String title, String owner, String status) {
			this.listId = listId;
			this.id = id;
			this.title = title;
			this.owner = owner;
			this.status = status;
		}

		@Override
		public String toString() {
			return title + owner + " " + "[" + status + "]";
		}
	}

	// Wait for any response with this requestId (OK or ERROR) and treat ERROR as
	// exception.
	private static Object[] waitForOk(RemoteSpace responses, String requestId) throws Exception {
		Object[] tuple = responses.get(
				new FormalField(Object.class), // resp code (OK / ERROR / etc.)
				new ActualField(requestId),
				new FormalField(Object.class),
				new FormalField(Object.class),
				new FormalField(Object.class),
				new FormalField(Object.class));

		Object code = tuple[0];

		if (TupleSpaces.RESP_OK.equals(code)) {
			return tuple;
		}

		if (TupleSpaces.RESP_ERROR.equals(code)) {
			// assuming error message at index 2
			String msg = tuple[2] != null ? tuple[2].toString() : "unknown error";
			throw new RuntimeException("Server error for request " + requestId + ": " + msg);
		}

		throw new RuntimeException("Unexpected response code for request " + requestId + ": " + code);
	}

	public static void loadTasksForList(
			Label statusLabel,
			ListView<TaskEntry> tasksView,
			String tasksUri,
			String listId) {
		setStatus(statusLabel, "Loading tasks...");

		new Thread(() -> {
			try {
				RemoteSpace tasks = new RemoteSpace(tasksUri);

				// Tuple: listId:String, taskId:String, title:String, owner:String,
				// status:String)
				List<Object[]> tuples = tasks.queryAll(
						new ActualField(listId),
						new FormalField(String.class),
						new FormalField(String.class),
						new FormalField(String.class),
						new FormalField(String.class));

				Platform.runLater(() -> {
					tasksView.getItems().clear();
					for (Object[] t : tuples) {
						tasksView.getItems().add(new TaskEntry(
								(String) t[0],
								(String) t[1],
								(String) t[2],
								(String) t[3],
								(String) t[4]));
					}
					setStatus(statusLabel, "Loaded " + tuples.size() + " tasks");
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					setStatus(statusLabel, "Failed: " + ex.getMessage());
				});
			}
		}, "load-tasks-for-list").start();
	}

	public static void addTaskToList(
			Label statusLabel,
			Button addTaskButton,
			String requestsUri,
			String responsesUri,
			String listId,
			String taskTitle,
			String taskOwner) {

		if (taskTitle == null || taskTitle.isBlank()) {
			setStatus(statusLabel, "Enter a task title");
			return;
		}

		setStatus(statusLabel, "Adding task...");
		addTaskButton.setDisable(true);

		new Thread(() -> {
			String requestId = UUID.randomUUID().toString();
			try {
				RemoteSpace requests = new RemoteSpace(requestsUri);
				RemoteSpace responses = new RemoteSpace(responsesUri);

				requests.put(TupleSpaces.CMD_TASK_ADD, requestId, listId, taskTitle, 
						taskOwner == null ? "" : taskOwner, "");

				Object[] resp = waitForOk(responses, requestId);

				String returnedTaskId = resp.length > 3 && resp[3] instanceof String ? (String) resp[3] : null;

				Platform.runLater(() -> {
					setStatus(statusLabel, returnedTaskId != null ? "Task added" : "Task added (no id returned)");
					addTaskButton.setDisable(false);
					// No manual refresh - notification system handles it automatically
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					setStatus(statusLabel, "Adding task failed: " + ex.getMessage());
					addTaskButton.setDisable(false);
				});
			}
		}, "add-task-via-requests").start();
	}

	public static void changeTaskStatus(
			Label statusLabel,
			Button changeStatusButton,
			String requestsUri,
			String responsesUri,
			String listId,
			String taskId,
			String newStatus) {
		setStatus(statusLabel, "Changing task status...");
		changeStatusButton.setDisable(true);

		new Thread(() -> {
			String requestId = UUID.randomUUID().toString();
			try {
				RemoteSpace requests = new RemoteSpace(requestsUri);
				RemoteSpace responses = new RemoteSpace(responsesUri);

				requests.put(TupleSpaces.CMD_TASK_STATUS, requestId, listId, taskId, newStatus, "");

				// TODO: Use this?
				Object[] resp = waitForOk(responses, requestId);
				
				Platform.runLater(() -> {
					setStatus(statusLabel, "Task status changed to " + newStatus);
					changeStatusButton.setDisable(false);
					// No manual refresh - notification system handles it automatically
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					setStatus(statusLabel, "Changing task status failed: " + ex.getMessage());
					changeStatusButton.setDisable(false);
				});
			}
		}, "change-task-status").start();
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

	public static void assignTaskToList(
			Label statusLabel,
			Button assignButton,
			String requestsUri,
			String responsesUri,
			String listId,
			String taskId,
			String owner) {
		
		if (owner == null || owner.isBlank()) {
			setStatus(statusLabel, "Enter a username to assign");
			return;
		}

		setStatus(statusLabel, "Assigning task...");
		assignButton.setDisable(true);

		new Thread(() -> {
			String requestId = UUID.randomUUID().toString();
			try {
				RemoteSpace requests = new RemoteSpace(requestsUri);
				RemoteSpace responses = new RemoteSpace(responsesUri);

				requests.put(TupleSpaces.CMD_TASK_ASSIGN, requestId, listId, taskId, owner, "");
				
				// TODO: Use this?
				Object[] resp = waitForOk(responses, requestId);

				Platform.runLater(() -> {
					setStatus(statusLabel, "Task assigned to " + owner);
					assignButton.setDisable(false);
					// No manual refresh - notification system handles it automatically
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					setStatus(statusLabel, "Assign failed: " + ex.getMessage());
					assignButton.setDisable(false);
				});
			}
		}, "assign-task").start();
	}

	public static void deleteTaskFromList(
        Label statusLabel,
        Button deleteButton,
        String requestsUri,
        String responsesUri,
        String listId,
        String taskId
) {
    if (taskId == null || taskId.isBlank()) {
        setStatus(statusLabel, "No task selected");
        return;
    }

    setStatus(statusLabel, "Deleting task...");
    deleteButton.setDisable(true);
	
    new Thread(() -> {
        String requestId = UUID.randomUUID().toString();
        try {
            RemoteSpace requests = new RemoteSpace(requestsUri);
            RemoteSpace responses = new RemoteSpace(responsesUri);

            requests.put(TupleSpaces.CMD_TASK_DELETE, requestId, listId, taskId, "", "");

            // Wait for ok else error
            waitForOk(responses, requestId);

            Platform.runLater(() -> {
                setStatus(statusLabel, "Task deleted");
                deleteButton.setDisable(false);
                // No manual refresh - notification system handles it automatically
            });
        } catch (Exception ex) {
            Platform.runLater(() -> {
                setStatus(statusLabel, "Delete failed: " + ex.getMessage());
                deleteButton.setDisable(false);
            });
        }
    }, "task-delete").start();
}

	public static void createToDoList(Label statusLabel, Button createToDoListButton, String requestsUri,
			String responsesUri, String listName) {
		if (listName == null || listName.isBlank()) {
			setStatus(statusLabel, "Enter a name");
			return;
		}

		setStatus(statusLabel, "Creating to do list...");
		createToDoListButton.setDisable(true);
		
		new Thread(() -> {
			String requestId = UUID.randomUUID().toString();
			try {
				RemoteSpace requests = new RemoteSpace(requestsUri);
				RemoteSpace responses = new RemoteSpace(responsesUri);

				// Send create request (a1 = listName)
				requests.put(TupleSpaces.CMD_LIST_CREATE, requestId, listName, "", "", "");

				// Wait for server response correlated by requestId
				Object[] resp = waitForOk(responses, requestId);

				// resp[2]=listId, resp[3]=listName (if provided by server)
				String createdId = resp.length > 2 && resp[2] instanceof String ? (String) resp[2] : null;
				String createdName = resp.length > 3 && resp[3] instanceof String ? (String) resp[3] : null;

				Platform.runLater(() -> {
					if (createdId != null) {
						setStatus(statusLabel, "Created: " + (createdName != null ? createdName : createdId));
					} else {
						setStatus(statusLabel, "Created (no id returned)");
					}
					createToDoListButton.setDisable(false);
					// No manual refresh - notification system handles it automatically
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					setStatus(statusLabel, "Creation of list failed: " + ex.getMessage());
					createToDoListButton.setDisable(false);
				});
			}
		}, "create-todo-list").start();
	}

}
