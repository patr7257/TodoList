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

            SequentialSpace todoLists = new SequentialSpace();
            repo.add("todoLists", todoLists);

            SequentialSpace counter = new SequentialSpace();
            repo.add("counter", counter);

            SequentialSpace users = new SequentialSpace();
            repo.add(TupleSpaces.USERS, users);

            // optional: lav nogle testbrugere
            users.put("alice");
            users.put("bob");

            // Tasks space 
            SequentialSpace tasks = new SequentialSpace();
            repo.add(TupleSpaces.TASKS, tasks);

            // Request
            SequentialSpace requests = new SequentialSpace();
            repo.add(TupleSpaces.REQUESTS, requests);

            // Responses (server -> client responses)
            SequentialSpace responses = new SequentialSpace();
            repo.add(TupleSpaces.RESPONSES, responses);

            // Notifications (server -> all clients broadcasts for auto-sync)
            SequentialSpace notifications = new SequentialSpace();
            repo.add(TupleSpaces.NOTIFICATIONS, notifications);


            //TODO
            // //Database.loadDatabase();
            // Hardcoded lists for testing frontend connection: tuples of (listId, listName)
            todoLists.put("l1", "Shopping");
            todoLists.put("l2", "School");
            todoLists.put("l3", "Work");
            todoLists.put("l4", "Chores");
            todoLists.put("l5", "Trips");

            // Open a gate so RemoteSpace clients can connect
            repo.addGate(ServerConfig.gateUri());

            // Counter should mirror actual todoLists size
            ServerConfig.syncCounterToTodoLists(counter, todoLists);

            System.out.println("Server started on " + ServerConfig.SERVER_IP + ":" + ServerConfig.PORT);
            System.out.println("Clients can connect now at: " + ServerConfig.spaceUri(""));

            ServerHandlerService service = new ServerHandlerService(todoLists, counter, users, tasks, requests, responses, notifications);
            Thread requestLoop = new Thread(service, "request-loop");
            requestLoop.setDaemon(false);
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
}
