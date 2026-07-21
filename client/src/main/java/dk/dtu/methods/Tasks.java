package dk.dtu.methods;

import dk.dtu.net.ApiModels.ListDto;
import dk.dtu.net.ApiModels.StateResponse;
import dk.dtu.net.ApiSession;
import javafx.application.Platform;
import javafx.scene.control.ListView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Service for task (item) operations, backed by the shared HTTP API.
 *
 * <p>Public signatures are preserved from the jSpace version (callers still
 * pass transport URI strings, now ignored). Owner/assignee is name-based in the
 * UI but id-based in the API: writes resolve the display name to an assignee id
 * via {@link ApiSession#idForName(String)}, reads show {@code assigneeName}.
 *
 * <p>The API does not expose a writable {@code year} on items yet, so
 * {@link #setTaskYear} throws {@link UnsupportedOperationException}.
 */
public class Tasks {

    private Tasks() {}

    public static void loadTasksForList(ListView<Helpers.TaskEntry> tasksView, String tasksUri, String listId) {
        loadTasksForList(tasksUri, listId, entries -> tasksView.getItems().setAll(entries));
    }

    public static void loadTasksForList(String tasksUri, String listId, Consumer<List<Helpers.TaskEntry>> onLoaded) {
        new Thread(() -> {
            try {
                StateResponse state = ApiSession.get().fetchState();
                List<Helpers.TaskEntry> entries = java.util.Collections.emptyList();
                if (state != null && state.lists() != null) {
                    for (ListDto l : state.lists()) {
                        if (l != null && listId != null && listId.equals(l.id())) {
                            entries = Helpers.toTaskEntries(l);
                            break;
                        }
                    }
                }
                final List<Helpers.TaskEntry> result = entries;
                Platform.runLater(() -> {
                    if (onLoaded != null) {
                        onLoaded.accept(result);
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                ApiSession.get().reportError(ex);
            }
        }, "load-tasks-for-list").start();
    }

    public static void addTask(String requestsUri, String responsesUri, String listId,
                               String taskTitle, String dueDate, String taskOwner) throws Exception {
        if (taskTitle == null || taskTitle.isBlank()) {
            throw new IllegalArgumentException("Task title cannot be empty");
        }
        String assigneeId = (taskOwner == null || taskOwner.isBlank())
                ? null : ApiSession.get().idForName(taskOwner);
        String dueAt = Helpers.dateToIsoInstant(dueDate);
        ApiSession.get().client().createItem(
                listId, taskTitle.trim(), null, null, assigneeId, null, dueAt, null);
    }

    public static void changeTaskStatus(String requestsUri, String responsesUri, String listId,
                                         String taskId, String newStatus) throws Exception {
        requireTaskId(taskId);
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("status", newStatus);
        ApiSession.get().client().updateItem(taskId, patch);
    }

    public static void changeTaskDueDate(String requestsUri, String responsesUri, String listId,
                                         String taskId, String newDueDate) throws Exception {
        requireTaskId(taskId);
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("dueAt", Helpers.dateToIsoInstant(newDueDate)); // null clears the due date
        ApiSession.get().client().updateItem(taskId, patch);
    }

    public static void assignTask(String requestsUri, String responsesUri, String listId,
                                  String taskId, String owner) throws Exception {
        requireTaskId(taskId);
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Owner cannot be empty");
        }
        String assigneeId = ApiSession.get().idForName(owner);
        if (assigneeId == null) {
            throw new IllegalArgumentException("Unknown user: " + owner);
        }
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("assigneeId", assigneeId);
        ApiSession.get().client().updateItem(taskId, patch);
    }

    public static void unassignTask(String requestsUri, String responsesUri, String listId, String taskId) throws Exception {
        requireListId(listId);
        requireTaskId(taskId);
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("assigneeId", null); // sent as JSON null to clear the assignee
        ApiSession.get().client().updateItem(taskId, patch);
    }

    public static void deleteTask(String requestsUri, String responsesUri, String taskId) throws Exception {
        requireTaskId(taskId);
        ApiSession.get().client().deleteItem(taskId);
    }

    public static void renameTask(String requestsUri, String responsesUri, String listId,
                                  String taskId, String newTitle) throws Exception {
        requireListId(listId);
        requireTaskId(taskId);
        if (newTitle == null || newTitle.isBlank()) {
            throw new IllegalArgumentException("Task title cannot be empty");
        }
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("text", newTitle.trim());
        ApiSession.get().client().updateItem(taskId, patch);
    }

    public static void setTaskPriority(String requestsUri, String responsesUri, String listId,
                                       String taskId, int priority) throws Exception {
        requireListId(listId);
        requireTaskId(taskId);
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("priority", priority);
        ApiSession.get().client().updateItem(taskId, patch);
    }

    public static void setTaskYear(String requestsUri, String responsesUri, String listId,
                                   String taskId, int year) throws Exception {
        requireListId(listId);
        requireTaskId(taskId);
        throw new UnsupportedOperationException("Editing task year is not supported by the todo API yet.");
    }

    public static void setTaskLocation(String requestsUri, String responsesUri, String listId,
                                       String taskId, String location) throws Exception {
        requireListId(listId);
        requireTaskId(taskId);
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("location", location != null ? location : "");
        ApiSession.get().client().updateItem(taskId, patch);
    }

    public static void setTaskDescription(String requestsUri, String responsesUri, String listId,
                                          String taskId, String description) throws Exception {
        requireListId(listId);
        requireTaskId(taskId);
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("description", description != null ? description : "");
        ApiSession.get().client().updateItem(taskId, patch);
    }

    public static void setTaskOrderBulk(String requestsUri, String responsesUri, String listId,
                                        List<String> orderedTaskIds) throws Exception {
        requireListId(listId);
        if (orderedTaskIds == null || orderedTaskIds.isEmpty()) {
            throw new IllegalArgumentException("Task order cannot be empty");
        }
        for (int i = 0; i < orderedTaskIds.size(); i++) {
            Map<String, Object> patch = new LinkedHashMap<>();
            patch.put("sort", i);
            ApiSession.get().client().updateItem(orderedTaskIds.get(i), patch);
        }
    }

    private static void requireListId(String listId) {
        if (listId == null || listId.isBlank()) {
            throw new IllegalArgumentException("List ID cannot be empty");
        }
    }

    private static void requireTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("Task ID cannot be empty");
        }
    }
}
