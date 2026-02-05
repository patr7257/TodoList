package dk.dtu.methods;

import dk.dtu.shared.TupleSpaces;
import org.jspace.ActualField;
import org.jspace.FormalField;
import org.jspace.RemoteSpace;

import java.util.UUID;

// Helper methods and data structures
public class Helpers {

    private Helpers() {
    }

    // Send request and wait for response
    public static Object[] sendAndWaitForResponse(
            String requestsUri,
            String responsesUri,
            String command,
            String a1, String a2, String a3, String a4) throws Exception {

        String requestId = UUID.randomUUID().toString();
        RemoteSpace requests = new RemoteSpace(requestsUri);
        RemoteSpace responses = new RemoteSpace(responsesUri);

        requests.put(command, requestId, a1, a2, a3, a4);
        return waitForOk(responses, requestId);
    }

    // Wait for response and validate it's OK (not ERROR)
    private static Object[] waitForOk(RemoteSpace responses, String requestId) throws Exception {
        Object[] tuple = responses.get(
                new FormalField(Object.class),
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
            String msg = tuple[2] != null ? tuple[2].toString() : "unknown error";
            throw new RuntimeException("Server error: " + msg);
        }

        throw new RuntimeException("Unexpected response code: " + code);
    }

    // Represents a todo list entry with ID and name
    public static class ListEntry {
        public final String id;
        public final String name;
        public final String owner;
        public final String taskColumnsJson;
        public final int priority;
        public final int year;
        public final int orderIndex;
        public final String location;
        public final String description;
        public int completionPercentage;
        public final int taskCount;
        public final int overdueTaskCount;

        public ListEntry(String id, String name, String owner, String taskColumnsJson, int priority, int year, int orderIndex, String location, String description, int completionPercentage, int taskCount, int overdueTaskCount) {
            this.id = id;
            this.name = name;
            this.owner = owner;
            this.taskColumnsJson = taskColumnsJson;
            this.priority = priority;
            this.year = year;
            this.orderIndex = orderIndex;
            this.location = location;
            this.description = description;
            this.completionPercentage = completionPercentage;
            this.taskCount = taskCount;
            this.overdueTaskCount = overdueTaskCount;
        }

        @Override
        public String toString() {
            return id + " - " + name;
        }
    }

    // Represents a task entry with all task details
    public static class TaskEntry {
        public final String listId;
        public final String id;
        public final String title;
        public final String owner;
        public final String status;
        public final String dueDate;
        public final int priority;
        public final int year;
        public final int orderIndex;
        public final String location;
        public final String description;

        public TaskEntry(String listId, String id, String title,
                String owner, String status, String dueDate, int priority, int year, int orderIndex, String location, String description) {
            this.listId = listId;
            this.id = id;
            this.title = title;
            this.owner = owner;
            this.status = status;
            this.dueDate = dueDate;
            this.priority = priority;
            this.year = year;
            this.orderIndex = orderIndex;
            this.location = location;
            this.description = description;
        }

        // Pretty status text for the UI
        public String statusToString() {
            if (status == null)
                return "";

            return switch (status.trim().toUpperCase()) {
                case "NOT_STARTED" -> "Not started yet";
                case "IN_PROGRESS" -> "In progress";
                case "DELAYED" -> "Delayed";
                case "NEED_HELP" -> "Needs help";
                case "DONE" -> "Done";
                default -> status; // fallback
            };
        }

        // Task name for the "Task" column
        public String nameToString() {
            return title != null ? title : "";
        }

        // Owner for the "Owner" column
        public String ownerToString() {
            return (owner == null || owner.isBlank()) ? "" : owner;
        }

        // Optional: nice due date text if you ever want it
        public String dueDateToString() {
            return (dueDate == null || dueDate.isBlank()) ? "" : dueDate;
        }

        public String locationToString() {
            return (location == null || location.isBlank()) ? "" : location;
        }

        public String descriptionToString() {
            return (description == null || description.isBlank()) ? "" : description;
        }

        @Override
        public String toString() {
            String who = ownerToString();
            String due = (dueDate == null || dueDate.isBlank()) ? "" : " (due: " + dueDate + ")";
            return nameToString() + " " + who + " [" + status + "]" + due;
        }
    }
}