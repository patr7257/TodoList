package dk.dtu.methods;

import dk.dtu.shared.TupleSpaces;
import org.jspace.ActualField;
import org.jspace.FormalField;
import org.jspace.RemoteSpace;

import java.util.UUID;

// Helper methods and data structures
public class Helpers {

    private Helpers() {}

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

        public ListEntry(String id, String name) {
            this.id = id;
            this.name = name;
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

        public TaskEntry(String listId, String id, String title, String owner, String status) {
            this.listId = listId;
            this.id = id;
            this.title = title;
            this.owner = owner;
            this.status = status;
        }

        @Override
        public String toString() {
            String who = (owner == null || owner.isBlank()) ? " " : "@" + owner;
            return title + " " + who + " [" + status + "]";
        }
    }
}
