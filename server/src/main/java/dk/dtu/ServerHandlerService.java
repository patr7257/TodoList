package dk.dtu;

import dk.dtu.model.Database;
import dk.dtu.shared.TupleSpaces;
import java.util.List;
import java.util.UUID;

import org.jspace.ActualField;
import org.jspace.FormalField;
import org.jspace.Space;

// Backend service handling requests and responses.
public class ServerHandlerService implements Runnable {
    private final Space todoLists;
    private final Space counter;
    private final Space users;
    private final Space tasks;
    private final Space requests;
    private final Space responses;
    private final Space notifications;

    public ServerHandlerService(Space todoLists, Space counter, Space users, Space tasks, Space requests,
            Space responses, Space notifications) {
        this.todoLists = todoLists;
        this.counter = counter;
        this.users = users;
        this.tasks = tasks;
        this.requests = requests;
        this.responses = responses;
        this.notifications = notifications;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Object[] tuple = requests.get(
                        new FormalField(String.class),
                        new FormalField(String.class),
                        new FormalField(Object.class),
                        new FormalField(Object.class),
                        new FormalField(Object.class),
                        new FormalField(Object.class));

                Request req = Request.fromTuple(tuple);
                switch (req.cmd()) {
                    case TupleSpaces.CMD_PING -> handlePing(req);
                    case TupleSpaces.CMD_CLIENT_DISCONNECT -> handleClientDisconnect(req);
                    case TupleSpaces.CMD_LIST_CREATE -> handleListCreate(req);
                    case TupleSpaces.CMD_TASK_ADD -> handleTaskAdd(req);
                    case TupleSpaces.CMD_TASK_STATUS -> handleTaskStatus(req);
                    case TupleSpaces.CMD_TASK_ASSIGN -> handleTaskAssign(req);
                    case TupleSpaces.CMD_LISTS_GET -> handleListsGet(req);
                    case TupleSpaces.CMD_TASKS_GET -> handleTasksGet(req);
                    case TupleSpaces.CMD_TASK_DELETE -> handleTaskDelete(req);
                    case TupleSpaces.CMD_LIST_DELETE -> handleListDelete(req);
                    default -> {
                        // Ignore unknown commands for now
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handlePing(Request req) throws InterruptedException {
        System.out.println("Received ping from client.");
        responses.put(TupleSpaces.RESP_OK, req.requestId(), "pong", "", "", "");
        // Optional: could respond with ok
    }

    private void handleClientDisconnect(Request req) {
        String username = (req.a1() instanceof String) ? (String) req.a1() : "";
        System.out.println("Client disconnected from server: " + username);
    }

    private void handleListCreate(Request req) throws InterruptedException {
        String requestId = req.requestId();
        String providedName = (req.a1() instanceof String) ? (String) req.a1() : null;
        
        final String listId;
        final String listName;
        synchronized (todoLists) {
            int count = Database.getTodoListCount(todoLists);
            listId = "l" + (count + 1);
            listName = (providedName != null && !providedName.isBlank())
                    ? providedName
                    : "New List " + (count + 1);

            todoLists.put(listId, listName);
            // Update counter
            counter.getp(new FormalField(Integer.class));
            counter.put(count + 1);
        }

        System.out.println("List created: " + listName);
        responses.put(TupleSpaces.RESP_OK, requestId, listId, listName, "", "");
        
        // Broadcast to all clients: "data changed", trigger refresh
        notifications.put(TupleSpaces.NOTIFY_DATA_CHANGED, System.currentTimeMillis(), "list_create", listId, listName, "");
    }

    private void handleTaskAdd(Request req) {
        String requestId = req.requestId();
        String listId = (req.a1() instanceof String) ? (String) req.a1() : null;

        String title = (req.a2() instanceof String) ? (String) req.a2() : null;
        String assignee = (req.a4() instanceof String) ? (String) req.a4() : "";

        if (listId == null || title == null) {
            return;
        }

        String taskId = UUID.randomUUID().toString();
        String status = "TODO";
        try {
            tasks.put(listId, taskId, title, assignee, status);
            System.out.println("Task added: " + title);

            // send OK response back
            responses.put(
                    TupleSpaces.RESP_OK,
                    requestId,
                    listId,
                    taskId,
                    title,
                    status);

            // Broadcast notification to all clients (send task title for user-friendly display)
            notifications.put(TupleSpaces.NOTIFY_DATA_CHANGED, System.currentTimeMillis(), "task_add", listId, title, "");

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void handleTaskStatus(Request req) throws InterruptedException {
        String requestId = req.requestId();
        String listId = (req.a1() instanceof String) ? (String) req.a1() : null;
        String taskId = (req.a2() instanceof String) ? (String) req.a2() : null;
        String newStatus = (req.a3() instanceof String) ? (String) req.a3() : null;

        if (listId == null || taskId == null || newStatus == null) {
            responses.put(TupleSpaces.RESP_ERROR, requestId, "Invalid parameters", "", "", "");
            return;
        }

        Object[] existing = tasks.getp(
                new ActualField(listId),
                new ActualField(taskId),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class));
        
        if (existing == null) {
            responses.put(TupleSpaces.RESP_ERROR, requestId, "Task not found", listId, taskId, "");
            return;
        }

        String title = (String) existing[2];
        String assignee = (String) existing[3];

        tasks.put(listId, taskId, title, assignee, newStatus);
        responses.put(TupleSpaces.RESP_OK, requestId, listId, taskId, title, newStatus);
        
        // Broadcast notification to all clients (include new status for display)
        notifications.put(TupleSpaces.NOTIFY_DATA_CHANGED, System.currentTimeMillis(), "task_status", listId, newStatus, "");

        System.out.println("Task status updated: " + newStatus);
    }

    private void handleListsGet(Request req) throws InterruptedException {
        String requestId = req.requestId();

        List<Object[]> all = todoLists.queryAll(
                new FormalField(String.class),
                new FormalField(String.class));

        for (Object[] l : all) {
            String listId = (String) l[0];
            String listName = (String) l[1];
            responses.put(TupleSpaces.RESP_OK, requestId, listId, listName, "", "");
        }
    }

    // Sends back all tasks for a given list
    private void handleTasksGet(Request req) throws InterruptedException {
        String requestId = req.requestId();
        String listId = (req.a1() instanceof String) ? (String) req.a1() : null;
        if (listId == null) {
            return;
        }
        List<Object[]> tuples = tasks.queryAll(
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class));

        for (Object[] t : tuples) {
            String tListId = (String) t[0];
            if (!tListId.equals(listId))
                continue;

            String taskId = (String) t[1];
            String title = (String) t[2];
            String assignee = (String) t[3];
            String status = (String) t[4];

            responses.put(TupleSpaces.RESP_OK, requestId, listId, taskId, title + "\t" + assignee, status);
        }
        responses.put(TupleSpaces.RESP_OK, requestId, "END", "", "", "");
    }

    // Delete a task
    private void handleTaskDelete(Request req) throws InterruptedException {
        String requestId = req.requestId();
        String taskId = (req.a2() instanceof String) ? (String) req.a2() : null;

        if (taskId == null) {
            return;
        }

        Object[] removed = tasks.getp(
                new FormalField(String.class),
                new ActualField(taskId),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class));

        if (removed == null) {
            responses.put(TupleSpaces.RESP_ERROR, requestId, "Task not found", "", taskId, "");
            return;
        }
        
        String deletedListId = (String) removed[0];

        responses.put(TupleSpaces.RESP_OK, requestId, "", taskId, "DELETED", "OK");
        
        // Broadcast notification to all clients
        notifications.put(TupleSpaces.NOTIFY_DATA_CHANGED, System.currentTimeMillis(), "task_delete", deletedListId, taskId, "");
    }

    private void handleListDelete(Request req) throws InterruptedException {
        String requestId = req.requestId();

        String listId = (req.a1() instanceof String) ? (String) req.a1() : null;

        if (listId == null) {
            return;
        }
        Object[] removed = todoLists.getp(
                new ActualField(listId),
                new FormalField(String.class));
        if (removed == null) {
            responses.put(TupleSpaces.RESP_ERROR, requestId, "List not found", listId, "", "");
            return;
        }
        String removedName = (removed[1] instanceof String) ? (String) removed[1] : "";

        System.out.println("List deleted: " + removedName);
        responses.put(TupleSpaces.RESP_OK, requestId, listId, removedName, "DELETED", "OK");
        
        // Broadcast notification to all clients
        notifications.put(TupleSpaces.NOTIFY_DATA_CHANGED, System.currentTimeMillis(), "list_delete", listId, removedName, "");
    }

    private void handleTaskAssign(Request req) {
        String requestId = req.requestId();
        String listId = (req.a1() instanceof String) ? (String) req.a1() : null;
        String taskId = (req.a2() instanceof String) ? (String) req.a2() : null;
        String newAssignee = (req.a3() instanceof String) ? (String) req.a3() : null;
    
        if (listId == null || taskId == null || newAssignee == null) {
            try { responses.put(TupleSpaces.RESP_ERROR, requestId, "Invalid parameters", "", "", ""); }
            catch (InterruptedException ignored) {}
            return;
        }
    
        try {
            // Validate assignee exists in users (hardcoded)
            Object[] u = users.queryp(new ActualField(newAssignee));
            if (u == null) {
                responses.put(TupleSpaces.RESP_ERROR, requestId, "Unknown assignee", listId, taskId, newAssignee);
                return;
            }
    
            Object[] existing = tasks.getp(
                    new ActualField(listId),
                    new ActualField(taskId),
                    new FormalField(String.class), // title
                    new FormalField(String.class), // assignee
                    new FormalField(String.class)  // status
            );
    
            if (existing == null) {
                responses.put(TupleSpaces.RESP_ERROR, requestId, "Task not found", listId, taskId, "");
                return;
            }
    
            String title = (String) existing[2];
            String status = (String) existing[4];
    
            tasks.put(listId, taskId, title, newAssignee, status);
    
            responses.put(TupleSpaces.RESP_OK, requestId, listId, taskId, newAssignee, "OK");
    
            notifications.put(TupleSpaces.NOTIFY_DATA_CHANGED, System.currentTimeMillis(),
                    "task_assign", listId, taskId, newAssignee);
    
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private record Request(String cmd, String requestId, Object a1, Object a2, Object a3, Object a4) {
        static Request fromTuple(Object[] tuple) {
            if (tuple == null || tuple.length < TupleSpaces.ARITY) {
                throw new IllegalArgumentException("Request tuple must have arity " + TupleSpaces.ARITY);
            }
            return new Request(
                    (String) tuple[0],
                    (String) tuple[1],
                    tuple[2],
                    tuple[3],
                    tuple[4],
                    tuple[5]);
        }
    }
}