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
            // Initialize persistence service
            PersistenceService persistenceService = new PersistenceService();
            
            SpaceRepository repo = new SpaceRepository();

            SequentialSpace todoLists = new SequentialSpace();
            // Expose under the shared name expected by clients.
            repo.add(TupleSpaces.LISTS, todoLists);
            // Backward-compatible alias (older clients/configs may still use this name).
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

            // Try to load existing session, fallback to preset data if none exists
            boolean sessionLoaded = persistenceService.loadSession(users, todoLists, tasks);
            if (!sessionLoaded) {
                System.out.println("Loading preset database...");
                Database.loadDatabase(users, todoLists, tasks);
                // Save the preset data
                persistenceService.saveSession(users, todoLists, tasks);
            }

            // Open a gate so RemoteSpace clients can connect
            repo.addGate(Config.getServerGateUri());

            // Initialize counter with current todoLists count
            counter.put(Database.getTodoListCount(todoLists));

            System.out.println(
                    "Server started.\n" +
                    "- Bind: tcp://" + Config.getServerBindHost() + ":" + Config.getPort() + "/\n" +
                    "- Clients connect to: " + Config.getClientBaseUri() + "\n" +
                    "Waiting for client requests..."
            );

            ServerHandlerService service = new ServerHandlerService(todoLists, counter, users, tasks, requests, responses, notifications, persistenceService);
            Thread requestLoop = new Thread(service, "request-loop");
            requestLoop.setDaemon(false);
            requestLoop.start();
            
            // Add shutdown hook to save data on exit
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down server...");
                persistenceService.saveSession(users, todoLists, tasks);
                System.out.println("Server stopped.");
            }, "shutdown-hook"));
            
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