package dk.dtu.methods;

import com.google.gson.Gson;
import dk.dtu.shared.Config;
import dk.dtu.shared.Defaults;
import dk.dtu.shared.TupleSpaces;
import javafx.application.Platform;
import javafx.scene.control.ListView;
import org.jspace.ActualField;
import org.jspace.FormalField;
import org.jspace.RemoteSpace;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

// Service for task operations
public class Tasks {

    private Tasks() {}

    private static final Gson GSON = new Gson();

    public static void loadTasksForList(
            ListView<Helpers.TaskEntry> tasksView,
            String tasksUri,
            String listId) {

        loadTasksForList(tasksUri, listId, entries -> {
            tasksView.getItems().setAll(entries);
        });
    }

    public static void loadTasksForList(
            String tasksUri,
            String listId,
            Consumer<List<Helpers.TaskEntry>> onLoaded) {

        new Thread(() -> {
            try {
                RemoteSpace tasks = new RemoteSpace(tasksUri);
                List<Object[]> tuples = tasks.queryAll(
                        new ActualField(listId),
                        new FormalField(String.class),
                        new FormalField(String.class),
                        new FormalField(String.class),
                        new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(Integer.class),
                    new FormalField(Integer.class),
                    new FormalField(Integer.class),
                    new FormalField(String.class),
                    new FormalField(String.class));

                if (tuples == null) {
                    tuples = java.util.Collections.emptyList();
                }

                tuples.sort(Comparator.comparingInt(t -> (t[8] instanceof Integer i) ? i : 0));
                List<Helpers.TaskEntry> entries = new java.util.ArrayList<>(tuples.size());

                for (Object[] t : tuples) {
                    entries.add(new Helpers.TaskEntry(
                            (String) t[0],
                            (String) t[1],
                            (String) t[2],
                            (String) t[3],
                            (String) t[4],
                            (String) t[5],
                            (t[6] instanceof Integer p) ? p : Defaults.PRIORITY,
                            (t[7] instanceof Integer y) ? y : Defaults.YEAR,
                            (t[8] instanceof Integer o) ? o : 0,
                            safe((String) t[9]),
                            safe((String) t[10])
                    ));
                }

                Platform.runLater(() -> {
                    if (onLoaded != null) {
                        onLoaded.accept(entries);
                    }
                });
                        
            } catch (Exception ex) {
                ex.printStackTrace();
                if (Config.isConnectionError(ex)) {
                    Config.handleConnectionError(ex);
                }
            }
        }, "load-tasks-for-list").start();
    }

    public static void addTask(
            String requestsUri,
            String responsesUri,
            String listId,
            String taskTitle,
            String dueDate,
            String taskOwner) throws Exception {

        if (taskTitle == null || taskTitle.isBlank()) {
            throw new IllegalArgumentException("Task title cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
            requestsUri,
            responsesUri,
            TupleSpaces.CMD_TASK_ADD,
            listId,
            taskTitle,
            dueDate != null ? dueDate : "",
            taskOwner != null ? taskOwner : "");
    }

    public static void changeTaskStatus(
            String requestsUri,
            String responsesUri,
            String listId,
            String taskId,
            String newStatus) throws Exception {

        Helpers.sendAndWaitForResponse(
            requestsUri,
            responsesUri,
            TupleSpaces.CMD_TASK_STATUS,
            listId,
            taskId,
            newStatus,
            "");
    }

    public static void changeTaskDueDate(
        String requestsUri,
        String responsesUri,
        String listId,
        String taskId,
        String newDueDate) throws Exception {

    Helpers.sendAndWaitForResponse(
            requestsUri,
            responsesUri,
            TupleSpaces.CMD_TASK_DUEDATE,
            listId,
            taskId,
            newDueDate != null ? newDueDate : "",
            ""
        );
    }


    public static void assignTask(
            String requestsUri,
            String responsesUri,
            String listId,
            String taskId,
            String owner) throws Exception {

        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Owner cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
            requestsUri,
            responsesUri,
            TupleSpaces.CMD_TASK_ASSIGN,
            listId,
            taskId,
            owner,
            "");
    }

    public static void unassignTask(
            String requestsUri,
            String responsesUri,
            String listId,
            String taskId) throws Exception {

        if (listId == null || listId.isBlank()) {
            throw new IllegalArgumentException("List ID cannot be empty");
        }
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("Task ID cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
                requestsUri,
                responsesUri,
                TupleSpaces.CMD_TASK_UNASSIGN,
                listId,
                taskId,
                "",
                "");
    }

    public static void deleteTask(
            String requestsUri,
            String responsesUri,
            String taskId) throws Exception {

        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("Task ID cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
            requestsUri,
            responsesUri,
            TupleSpaces.CMD_TASK_DELETE,
            "",
            taskId,
            "",
            "");
    }

    public static void renameTask(
            String requestsUri,
            String responsesUri,
            String listId,
            String taskId,
            String newTitle) throws Exception {

        if (listId == null || listId.isBlank()) {
            throw new IllegalArgumentException("List ID cannot be empty");
        }
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("Task ID cannot be empty");
        }
        if (newTitle == null || newTitle.isBlank()) {
            throw new IllegalArgumentException("Task title cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
                requestsUri,
                responsesUri,
                TupleSpaces.CMD_TASK_RENAME,
                listId,
                taskId,
                newTitle,
                "");
    }

    public static void setTaskPriority(
            String requestsUri,
            String responsesUri,
            String listId,
            String taskId,
            int priority) throws Exception {

        if (listId == null || listId.isBlank()) {
            throw new IllegalArgumentException("List ID cannot be empty");
        }
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("Task ID cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
                requestsUri,
                responsesUri,
                TupleSpaces.CMD_TASK_PRIORITY_SET,
                listId,
                taskId,
                Integer.toString(priority),
                "");
    }

    public static void setTaskYear(
            String requestsUri,
            String responsesUri,
            String listId,
            String taskId,
            int year) throws Exception {

        if (listId == null || listId.isBlank()) {
            throw new IllegalArgumentException("List ID cannot be empty");
        }
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("Task ID cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
                requestsUri,
                responsesUri,
                TupleSpaces.CMD_TASK_YEAR_SET,
                listId,
                taskId,
                Integer.toString(year),
                "");
    }

    public static void setTaskLocation(
            String requestsUri,
            String responsesUri,
            String listId,
            String taskId,
            String location) throws Exception {

        if (listId == null || listId.isBlank()) {
            throw new IllegalArgumentException("List ID cannot be empty");
        }
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("Task ID cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
                requestsUri,
                responsesUri,
                TupleSpaces.CMD_TASK_LOCATION_SET,
                listId,
                taskId,
                location != null ? location : "",
                "");
    }

    public static void setTaskDescription(
            String requestsUri,
            String responsesUri,
            String listId,
            String taskId,
            String description) throws Exception {

        if (listId == null || listId.isBlank()) {
            throw new IllegalArgumentException("List ID cannot be empty");
        }
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("Task ID cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
                requestsUri,
                responsesUri,
                TupleSpaces.CMD_TASK_DESCRIPTION_SET,
                listId,
                taskId,
                description != null ? description : "",
                "");
    }

    public static void setTaskOrderBulk(
            String requestsUri,
            String responsesUri,
            String listId,
            List<String> orderedTaskIds) throws Exception {

        if (listId == null || listId.isBlank()) {
            throw new IllegalArgumentException("List ID cannot be empty");
        }
        if (orderedTaskIds == null || orderedTaskIds.isEmpty()) {
            throw new IllegalArgumentException("Task order cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
                requestsUri,
                responsesUri,
                TupleSpaces.CMD_TASK_ORDER_BULK_SET,
                listId,
                GSON.toJson(orderedTaskIds),
                "",
                "");
    }
    private static String safe(String s) {
        return s != null ? s : "";
    }
} 
