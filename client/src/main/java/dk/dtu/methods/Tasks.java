package dk.dtu.methods;

import dk.dtu.shared.TupleSpaces;
import javafx.application.Platform;
import javafx.scene.control.ListView;
import org.jspace.ActualField;
import org.jspace.FormalField;
import org.jspace.RemoteSpace;

import java.util.List;

// Service for task operations
public class Tasks {

    private Tasks() {}

    public static void loadTasksForList(
            ListView<Helpers.TaskEntry> tasksView,
            String tasksUri,
            String listId) {

        new Thread(() -> {
            try {
                RemoteSpace tasks = new RemoteSpace(tasksUri);
                List<Object[]> tuples = tasks.queryAll(
                        new ActualField(listId),
                        new FormalField(String.class),
                        new FormalField(String.class),
                        new FormalField(String.class),
                        new FormalField(String.class));

                Platform.runLater(() -> {
                    tasksView.getItems().clear();
                    for (Object[] t : tuples) {
                        tasksView.getItems().add(new Helpers.TaskEntry(
                                (String) t[0],
                                (String) t[1],
                                (String) t[2],
                                (String) t[3],
                                (String) t[4]));
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }, "load-tasks-for-list").start();
    }

    public static void addTask(
            String requestsUri,
            String responsesUri,
            String listId,
            String taskTitle,
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
            "",
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
}
