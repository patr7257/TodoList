package dk.dtu;

import org.jspace.FormalField;
import org.jspace.Space;

// Backend service handling requests and responses.
public class ServerHandlerService implements Runnable {
    private final Space todoLists;
    private final Space counter;
    private final Space users;
    private final Space requests;
    private final Space responses;

    public ServerHandlerService(Space todoLists, Space counter, Space users, Space requests, Space responses) {
        this.todoLists = todoLists;
        this.counter = counter;
        this.users = users;
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

    private void handlePing(Request req) {
        System.out.println("handlePingRequest");
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

    // Placeholders for later
    private void handleTaskAdd(Request req) {
        System.out.println("handleTaskAddRequest");
    }

    private void handleTaskStatus(Request req) {
        System.out.println("handleTaskStatusRequest");
    }

    private void handleTaskAssign(Request req) {
        System.out.println("handleTaskAssignRequest");
    }

    private void handleListsGet(Request req) {
        System.out.println("handleListsGetRequest");
    }

    private void handleTasksGet(Request req) {
        System.out.println("handleTasksGetRequest");
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