package dk.dtu.methods;

import dk.dtu.shared.Config;
import dk.dtu.shared.TupleSpaces;
import javafx.application.Platform;
import javafx.scene.control.ListView;
import org.jspace.FormalField;
import org.jspace.RemoteSpace;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Service for todo list operations
public class Lists {

    private Lists() {}

    public static void loadTodoLists(ListView<Helpers.ListEntry> listsView, String todoListsUri) {
        new Thread(() -> {
            try {
                RemoteSpace todoLists = new RemoteSpace(todoListsUri);
                // Lists now have 3 fields: (listId, listName, completionPercentage)
                List<Object[]> tuples = todoLists.queryAll(
                        new FormalField(String.class),
                        new FormalField(String.class),
                        new FormalField(Integer.class));

                // Query all tasks to count them per list
                RemoteSpace tasks = new RemoteSpace(Config.getTasksUri());
                List<Object[]> allTasks = tasks.queryAll(
                        new FormalField(String.class),
                        new FormalField(String.class),
                        new FormalField(String.class),
                        new FormalField(String.class),
                        new FormalField(String.class),
                        new FormalField(String.class));

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

                Platform.runLater(() -> {
                    listsView.getItems().clear();
                    for (Object[] t : tuples) {
                        String listId = (String) t[0];
                        String listName = (String) t[1];
                        int count = taskCounts.getOrDefault(listId, 0);
                        int completion = (Integer) t[2];
                        int overdueCount = overdueCounts.getOrDefault(listId, 0);
                        listsView.getItems().add(new Helpers.ListEntry(listId, listName, completion, count, overdueCount));
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }, "load-todo-lists").start();
    }

    public static void createTodoList(
            String requestsUri,
            String responsesUri,
            String listName) throws Exception {
        
        if (listName == null || listName.isBlank()) {
            throw new IllegalArgumentException("List name cannot be empty");
        }

        Helpers.sendAndWaitForResponse(
            requestsUri,
            responsesUri,
            TupleSpaces.CMD_LIST_CREATE,
            listName, "", "", "");
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
}
