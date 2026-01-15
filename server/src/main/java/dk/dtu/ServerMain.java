package dk.dtu;

import dk.dtu.shared.Config;
import dk.dtu.shared.TupleSpaces;
import org.jspace.*;

// Main server application
// Sets up jSpace spaces, loads preset data, and starts request handling loop
// Listens for client requests and processes them accordingly
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

            // Load preset data into the system
            Database.loadDatabase(users, todoLists, tasks);

            // Open a gate so RemoteSpace clients can connect
            repo.addGate(Config.getServerGateUri());

            // Initialize counter with current todoLists count
            counter.put(Database.getTodoListCount(todoLists));

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
}