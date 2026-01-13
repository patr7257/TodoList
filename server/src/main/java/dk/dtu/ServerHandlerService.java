package dk.dtu;

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

    public ServerHandlerService(Space todoLists, Space counter, Space users, Space tasks, Space requests, Space responses) {
        this.todoLists = todoLists;
        this.counter = counter;
        this.users = users;
        this.tasks = tasks;
        this.requests = requests;
        this.responses = responses;
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
                        new FormalField(Object.class)
                );

                Request req = Request.fromTuple(tuple);
                switch (req.cmd()) {
                    case TupleSpaces.CMD_PING -> handlePing(req);
                    case TupleSpaces.CMD_LIST_CREATE -> handleListCreate(req);
                    case TupleSpaces.CMD_TASK_ADD -> handleTaskAdd(req);
                    case TupleSpaces.CMD_TASK_STATUS -> handleTaskStatus(req);
                    case TupleSpaces.CMD_TASK_ASSIGN -> handleTaskAssign(req);
                    case TupleSpaces.CMD_LISTS_GET -> handleListsGet(req);
                    case TupleSpaces.CMD_TASKS_GET -> handleTasksGet(req);
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
        System.out.println("handlePingRequest");
        responses.put(TupleSpaces.RESP_OK, req.requestId(), "pong", "", "", "");
        // Optional: could respond with ok
    }

    private void handleListCreate(Request req) throws InterruptedException {
        String requestId = req.requestId();
        String providedName = (req.a1() instanceof String) ? (String) req.a1() : null;

        final String listId;
        final String listName;
        synchronized (todoLists) {
            int count = ServerConfig.getTodoListCount(todoLists);
            listId = "l" + (count + 1);
            listName = (providedName != null && !providedName.isBlank())
                    ? providedName
                    : "New List " + (count + 1);

            todoLists.put(listId, listName);
            ServerConfig.syncCounterToTodoLists(counter, todoLists);
        }

        System.out.println("Created list: " + listId + " - " + listName);
        responses.put(TupleSpaces.RESP_OK, requestId, listId, listName, "", "");
    }

    private void handleTaskAdd(Request req) {
        System.out.println("handleTaskAddRequest");
        
        String requestId = req.requestId();
        String listId = (req.a1() instanceof String) ? (String) req.a1() : null;

        String title = (req.a2() instanceof String) ? (String) req.a2() : null;
        String owner = (req.a3() instanceof String) ? (String) req.a3() : null;

        if (listId == null || title == null) {
            System.out.println("Invalid parameters for adding task");
            return;
        }

        String taskId = UUID.randomUUID().toString();
        String status = "TODO";
        try {
            tasks.put(listId, taskId, title, owner == null ? "" : owner, status);
            System.out.println("Added task: " + taskId + " - " + title + " to list " + listId);

        // send OK response back 
        responses.put(
            TupleSpaces.RESP_OK,
            requestId,
            listId,
            taskId,
            title,
            status
        );
        
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void handleTaskStatus(Request req) throws InterruptedException {
        System.out.println("handleTaskStatusRequest");
        String requestId = req.requestId();
        String listId = (req.a1() instanceof String) ? (String) req.a1() : null;
        String taskId = (req.a2() instanceof String) ? (String) req.a2() : null;
        String newStatus = (req.a3() instanceof String) ? (String) req.a3() : null;

        if (listId == null || taskId == null || newStatus == null) {
            // Invalid parameters
            System.out.println("Invalid parameters for updating task status");
            responses.put(TupleSpaces.RESP_ERROR, requestId, "Invalid parameters", "", "", "");
            return;
        }

        Object[] existing = tasks.getp(
            new ActualField(listId),
            new ActualField(taskId),
            new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(String.class)
        );

        if (existing == null) {
            responses.put(TupleSpaces.RESP_ERROR, requestId, "Task not found", listId, taskId, "");
            return;
        }

        String title = (String) existing[2];
        String owner = (String) existing[3];

        tasks.put(listId, taskId, title, owner, newStatus);
        responses.put(TupleSpaces.RESP_OK, requestId, listId, taskId, title, newStatus);

        System.out.println("Updated task status: " + taskId + " to " + newStatus + " in list " + listId);
    }

    private void handleTaskAssign(Request req) {
        System.out.println("handleTaskAssignRequest");
    }

    private void handleListsGet(Request req) throws InterruptedException {
        System.out.println("handleListsGetRequest");
        String requestId = req.requestId();

        List<Object[]> all = todoLists.queryAll(
            new FormalField(String.class),
            new FormalField(String.class)
        );

        for (Object[] l : all) {
            String listId = (String) l[0];
            String listName = (String) l[1];
            responses.put(TupleSpaces.RESP_OK, requestId, listId, listName, "", "");
        }
    }

    // Sends back all tasks for a given list
    private void handleTasksGet(Request req) throws InterruptedException {
        System.out.println("handleTasksGetRequest");
        String requestId = req.requestId();
        String listId = (req.a1() instanceof String) ? (String) req.a1() : null;
        if (listId == null) {
            // Invalid parameters
            System.out.println("Invalid parameters for getting tasks");
            return;
        }
        List<Object[]> tuples = tasks.queryAll(
            new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(String.class)
        );

        
        for (Object[] t : tuples) {
            String tListId = (String) t[0];
            if (!tListId.equals(listId)) continue;

            String taskId = (String) t[1];
            String title = (String) t[2];
            String status = (String) t[4];

            responses.put(TupleSpaces.RESP_OK, requestId, listId, taskId, title, status);
        }
        responses.put(TupleSpaces.RESP_OK, requestId, "END", "", "", "");
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
                    tuple[5]
            );
        }
    }
}