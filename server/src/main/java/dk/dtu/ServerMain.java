package dk.dtu;

import dk.dtu.shared.Config;
import dk.dtu.shared.TupleSpaces;
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
            repo.addGate(Config.getServerGateUri());

            // Counter should mirror actual todoLists size
            syncCounterToTodoLists(counter, todoLists);

            System.out.println("Server started on " + Config.SERVER_IP + ":" + Config.PORT + ".\nWaiting for client requests...");

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
    
    // Helper methods for counter management
    public static int getTodoListCount(Space todoLists) throws InterruptedException {
        return todoLists.queryAll(
                new FormalField(String.class),
                new FormalField(String.class)).size();
    }

    public static void syncCounterToTodoLists(Space counter, Space todoLists) throws InterruptedException {
        int count = getTodoListCount(todoLists);
        counter.getp(new FormalField(Integer.class));
        counter.put(count);
    }
}
