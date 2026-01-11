package dk.dtu;

import org.jspace.*;

/**
 * Main server application
 * Minimal version (for learning):
 * - Create a SpaceRepository
 * - Open a TCP gate so clients can connect using RemoteSpace
 */
public class ServerMain {

    // Exposed spaces (make them static so handlers can access them)
    private static SequentialSpace todoLists;
    private static SequentialSpace counter;
    private static SequentialSpace users;
    private static SequentialSpace requests;
    private static SequentialSpace responses;

    public static void main(String[] args) {
        try {
            SpaceRepository repo = new SpaceRepository();

            // The one space we expose for now
            todoLists = new SequentialSpace();
            repo.add("todoLists", todoLists);

            // Shared counter space (single tuple: (count:int))
            counter = new SequentialSpace();
            repo.add("counter", counter);
            counter.put(0);

            users = new SequentialSpace();
            repo.add(TupleSpaces.USERS, users);

            // optional: lav nogle testbrugere
            users.put("alice");
            users.put("bob");

            // Request
            requests = new SequentialSpace();
            repo.add(TupleSpaces.REQUESTS, requests);

            // Responses (server -> client responses)
            responses = new SequentialSpace();
            repo.add(TupleSpaces.RESPONSES, responses);

            // Hardcoded lists for testing frontend connection: tuples of (listId, listName)
            todoLists.put("l1", "Shopping");
            todoLists.put("l2", "School");
            todoLists.put("l3", "Work");
            todoLists.put("l4", "Chores");
            todoLists.put("l5", "Trips");

            // Open a gate so RemoteSpace clients can connect
            String host = "127.0.0.1";
            int port = 9001;
            repo.addGate("tcp://" + host + ":" + port + "/?keep");

            String todoListsUri = "tcp://" + host + ":" + port + "/todoLists?keep";
            String counterUri = "tcp://" + host + ":" + port + "/counter?keep";
            String requestsUri = "tcp://" + host + ":" + port + "/" + TupleSpaces.REQUESTS + "?keep";
            String usersUri = "tcp://" + host + ":" + port + "/" + TupleSpaces.USERS + "?keep";
            String responsesUri = "tcp://" + host + ":" + port + "/" + TupleSpaces.RESPONSES + "?keep";

            System.out.println("Server started.");
            System.out.println("todoLists URI: " + todoListsUri);
            System.out.println("counter URI: " + counterUri);
            System.out.println("requests URI: " + requestsUri);
            System.out.println("responses URI: " + responsesUri);
            System.out.println("users URI: " + usersUri);

            Thread requestLoop = new Thread(() -> handleRequests(requests), "request-loop");
            requestLoop.start();
            // Keep the server process alive
            while (true) {
                Thread.sleep(60_000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleRequests(Space requests) {
        try {
            while (true) {
                System.out.println("hej");
                // Wait for a ping request: (cmd, requestId, a1, a2, a3, a4)
                Object[] req = requests.get(
                        new FormalField(String.class), // cmd
                        new FormalField(String.class), // requestId
                        new FormalField(Object.class),
                        new FormalField(Object.class),
                        new FormalField(Object.class),
                        new FormalField(Object.class));

                String cmdType = (String) req[0];
                switch (cmdType) {
                    case TupleSpaces.CMD_PING:
                        System.out.println("ping");
                        handlePingRequest(req);
                        break;
                    case TupleSpaces.CMD_LIST_CREATE:
                        System.out.println("listc");
                        handleListCreateRequest(req);
                        break;
                    case TupleSpaces.CMD_TASK_ADD:
                        handleTaskAddRequest(req);
                        break;
                    case TupleSpaces.CMD_TASK_STATUS:
                        handleTaskStatusRequest(req);
                        break;
                    case TupleSpaces.CMD_TASK_ASSIGN:
                        handleTaskAssignRequest(req);
                        break;
                    case TupleSpaces.CMD_LISTS_GET:
                        handleListsGetRequest(req);
                        break;
                    case TupleSpaces.CMD_TASKS_GET:
                        handleTasksGetRequest(req);
                        break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Request loop interrupted");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleTasksGetRequest(Object[] req) {
        System.out.println("handleTasksGetRequest");

    }

    private static void handleListsGetRequest(Object[] req) {
        System.out.println("handleListsGetRequest");

    }

    private static void handleTaskAssignRequest(Object[] req) {
        System.out.println("handleTaskAssignRequest");

    }

    private static void handleTaskStatusRequest(Object[] req) {
        System.out.println("handleTaskStatusRequest");
    }

    private static void handleTaskAddRequest(Object[] req) {
        System.out.println("handleTaskAddRequest");

    }

    private static void handleListCreateRequest(Object[] req) {
        System.out.println("handleListCreateRequest");
        try {
            // req format: (cmd, requestId, a1, a2, a3, a4)
            String requestId = (String) req[1];

            // Get and increment counter
            Object[] c = counter.get(new FormalField(Integer.class));
            int count = (Integer) c[0];
            count++;
            counter.put(count);

            // Create a new list id and a name (use provided name if any)
            String listId = "l" + count;
            String provided = null;
            if (req.length > 2 && req[2] instanceof String) {
                provided = (String) req[2];
            }
            String listName = (provided != null && !provided.isBlank()) ? provided : "New List " + count;

            // Store list tuple: (listId, listName) in the todoLists space
            todoLists.put(listId, listName);

            System.out.println("Created list: " + listId + " - " + listName);

            // send OK response back
            if (responses != null) {
                responses.put(TupleSpaces.RESP_OK, requestId, listId, listName, "", "");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("List creation interrupted");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handlePingRequest(Object[] req) {
        System.out.println("handlePingRequest");
    }

}
