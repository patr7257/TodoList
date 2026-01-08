package dk.dtu;

import org.jspace.*;

/**
 * Main server application
 * Minimal version (for learning):
 * - Create a SpaceRepository
 * - Open a TCP gate so clients can connect using RemoteSpace
 */
public class ServerMain {

    public static void main(String[] args) {
        try {
            SpaceRepository repo = new SpaceRepository();

            // The one space we expose for now
            SequentialSpace todoLists = new SequentialSpace();
            repo.add("todoLists", todoLists);

            // Shared counter space (single tuple: (count:int))
            SequentialSpace counter = new SequentialSpace();
            repo.add("counter", counter);
            counter.put(0);

            // Request
            SequentialSpace requests = new SequentialSpace();
            repo.add(TupleSpaces.REQUESTS, requests);

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

            System.out.println("Server started.");
            System.out.println("todoLists URI: " + todoListsUri);
            System.out.println("counter URI: " + counterUri);
            System.out.println("requests URI: " + requestsUri);

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
                        new ActualField(TupleSpaces.CMD_PING),
                        new FormalField(String.class),
                        new FormalField(Object.class),
                        new FormalField(Object.class),
                        new FormalField(Object.class),
                        new FormalField(Object.class));

                String cmdType = (String) req[0];
                switch (cmdType) {
                    case "ping":
                        System.out.println("ping");
                        handlePingRequest(req);
                        break;
                    case "list_create":
                        System.out.println("listc");
                        handleListCreateRequest(req);
                        break;
                    case "task_add":
                        handleTaskAddRequest(req);
                        break;
                    case "task_status":
                        handleTaskStatusRequest(req);
                        break;
                    case "task_assign":
                        handleTaskAssignRequest(req);
                        break;
                    case "lists_get":
                        handleListsGetRequest(req);
                        break;
                    case "tasks_get":
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
    }

    private static void handlePingRequest(Object[] req) {
        System.out.println("handlePingRequest");
    }

}
