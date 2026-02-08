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

import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

// Service for todo list operations
public class Lists {

    private Lists() {}

    private static final Gson GSON = new Gson();

    public static void loadTodoLists(ListView<Helpers.ListEntry> listsView, String todoListsUri) {
        loadTodoLists(todoListsUri, entries -> {
            listsView.getItems().setAll(entries);
        });
    }

    public static void loadTodoLists(String todoListsUri, Consumer<List<Helpers.ListEntry>> onLoaded) {
        new Thread(() -> {
            try {
                RemoteSpace todoLists = new RemoteSpace(todoListsUri);
                // Lists now have 10 fields: (listId, listName, completionPercentage, owner, taskColumnsJson, priority, year, orderIndex, location, description)
                List<Object[]> tuples = todoLists.queryAll(
                        new FormalField(String.class),
                        new FormalField(String.class),
                    new FormalField(Integer.class),
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

                // Query all tasks to count them per list
                RemoteSpace tasks = new RemoteSpace(Config.getTasksUri());
                List<Object[]> allTasks = tasks.queryAll(
                        new FormalField(String.class),
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

                if (allTasks == null) {
                    allTasks = java.util.Collections.emptyList();
                }

                // Count tasks per list
                Map<String, Integer> taskCounts = new HashMap<>();
                Map<String, Integer> overdueCounts = new HashMap<>();
                
                // Get current date for comparison
                java.time.LocalDate today = java.time.LocalDate.now();
                
                for (Object[] task : allTasks) {
                    String taskListId = (String) task[0];
                    String status = (String) task[4];
                    String dueDate = (String) task[5];
                    
                    taskCounts.put(taskListId, taskCounts.getOrDefault(taskListId, 0) + 1);
                    if (dueDate != null && !dueDate.isEmpty() && !"DONE".equals(status)) {
                        try {
                            java.time.LocalDate due = java.time.LocalDate.parse(dueDate);
                            if (due.isBefore(today)) {
                                overdueCounts.put(taskListId, overdueCounts.getOrDefault(taskListId, 0) + 1);
                            }
                        } catch (Exception e) {
                            // Invalid date format, skip
                        }
                    }
                }

                tuples.sort(Comparator.comparingInt(t -> (t[7] instanceof Integer i) ? i : 0));
                List<Helpers.ListEntry> entries = new java.util.ArrayList<>(tuples.size());
                for (Object[] t : tuples) {
                    String listId = (String) t[0];
                    String listName = (String) t[1];
                    int count = taskCounts.getOrDefault(listId, 0);
                    int completion = (Integer) t[2];
                    String owner = (String) t[3];
                    String taskColumnsJson = (String) t[4];
                    int priority = (t[5] instanceof Integer p) ? p : Defaults.PRIORITY;
                    int year = (t[6] instanceof Integer y) ? y : Defaults.YEAR;
                    int orderIndex = (t[7] instanceof Integer o) ? o : 0;
                    String location = (String) t[8];
                    String description = (String) t[9];
                    int overdueCount = overdueCounts.getOrDefault(listId, 0);
                    entries.add(new Helpers.ListEntry(
                            listId,
                            listName,
                            owner,
                            taskColumnsJson,
                            priority,
                            year,
                            orderIndex,
                            safe(location),
                            safe(description),
                            completion,
                            count,
                            overdueCount));
                }

                Platform.runLater(() -> {
                    if (onLoaded != null) {
                        onLoaded.accept(entries);
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }, "load-todo-lists").start();
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    public static void setListOrderBulk(
            String requestsUri,
            String responsesUri,
            List<String> orderedListIds) throws Exception {

        if (orderedListIds == null || orderedListIds.isEmpty()) {
            throw new IllegalArgumentException("List order cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
                requestsUri,
                responsesUri,
                TupleSpaces.CMD_LIST_ORDER_BULK_SET,
                GSON.toJson(orderedListIds),
                "",
                "",
                "");
    }

    public static void setListPriority(String requestsUri, String responsesUri, String listId, int priority) throws Exception {
        if (listId == null || listId.isBlank()) {
            throw new IllegalArgumentException("List ID cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
                requestsUri,
                responsesUri,
                TupleSpaces.CMD_LIST_PRIORITY_SET,
                listId,
                Integer.toString(priority),
                "",
                "");
    }

    public static void setListYear(String requestsUri, String responsesUri, String listId, int year) throws Exception {
        if (listId == null || listId.isBlank()) {
            throw new IllegalArgumentException("List ID cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
                requestsUri,
                responsesUri,
                TupleSpaces.CMD_LIST_YEAR_SET,
                listId,
                Integer.toString(year),
                "",
                "");
    }

    public static void setListLocation(String requestsUri, String responsesUri, String listId, String location) throws Exception {
        if (listId == null || listId.isBlank()) {
            throw new IllegalArgumentException("List ID cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
                requestsUri,
                responsesUri,
                TupleSpaces.CMD_LIST_LOCATION_SET,
                listId,
                location != null ? location : "",
                "",
                "");
    }

    public static void setListDescription(String requestsUri, String responsesUri, String listId, String description) throws Exception {
        if (listId == null || listId.isBlank()) {
            throw new IllegalArgumentException("List ID cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
                requestsUri,
                responsesUri,
                TupleSpaces.CMD_LIST_DESCRIPTION_SET,
                listId,
                description != null ? description : "",
                "",
                "");
    }

    public static void renameTodoList(String requestsUri, String responsesUri, String listId, String newName) throws Exception {
        if (listId == null || listId.isBlank()) {
            throw new IllegalArgumentException("List ID cannot be empty");
        }
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("List name cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
                requestsUri,
                responsesUri,
                TupleSpaces.CMD_LIST_RENAME,
                listId,
                newName,
                "",
                "");
    }

    public static void setListOwner(String requestsUri, String responsesUri, String listId, String owner) throws Exception {
        if (listId == null || listId.isBlank()) {
            throw new IllegalArgumentException("List ID cannot be empty");
        }
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Owner cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
                requestsUri,
                responsesUri,
                TupleSpaces.CMD_LIST_OWNER_SET,
                listId,
                owner,
                "",
                "");
    }

    public static void clearListOwner(String requestsUri, String responsesUri, String listId) throws Exception {
        if (listId == null || listId.isBlank()) {
            throw new IllegalArgumentException("List ID cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
                requestsUri,
                responsesUri,
                TupleSpaces.CMD_LIST_OWNER_CLEAR,
                listId,
                "",
                "",
                "");
    }

    public static void setTaskColumnsForList(String requestsUri, String responsesUri, String listId, String taskColumnsJson) throws Exception {
        if (listId == null || listId.isBlank()) {
            throw new IllegalArgumentException("List ID cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
                requestsUri,
                responsesUri,
                TupleSpaces.CMD_LIST_COLUMNS_SET,
                listId,
                taskColumnsJson != null ? taskColumnsJson : "",
                "",
                "");
    }

    public static void createTodoList(
            String requestsUri,
            String responsesUri,
            String listName,
            String owner) throws Exception {
        
        if (listName == null || listName.isBlank()) {
            throw new IllegalArgumentException("List name cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
            requestsUri,
            responsesUri,
            TupleSpaces.CMD_LIST_CREATE,
            listName,
            owner != null ? owner : "",
            "",
            "");
    }

    public static void deleteTodoList(
            String requestsUri,
            String responsesUri,
            String listId) throws Exception {
        
        if (listId == null || listId.isBlank()) {
            throw new IllegalArgumentException("List ID cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
            requestsUri,
            responsesUri,
            TupleSpaces.CMD_LIST_DELETE,
            listId, "", "", "");
    }

    /**
     * Fetch the saved task columns JSON for a single list.
          * Lists have 10 fields: (listId, listName, completionPercentage, owner, taskColumnsJson, priority, year, orderIndex, location, description)
     */
    public static String getTaskColumnsJsonForList(String todoListsUri, String listId) throws Exception {
        if (listId == null || listId.isBlank()) {
            throw new IllegalArgumentException("List ID cannot be empty");
        }

        RemoteSpace todoLists = new RemoteSpace(todoListsUri);
        Object[] tuple = todoLists.query(
                new ActualField(listId),
                new FormalField(String.class),
                new FormalField(Integer.class),
                new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class)
        );

        if (tuple == null || tuple.length < 10) {
            return "";
        }

        return tuple[4] != null ? tuple[4].toString() : "";
    }
}
