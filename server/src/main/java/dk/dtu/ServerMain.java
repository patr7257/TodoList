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

            System.out.println("=".repeat(70));
            System.out.println("SERVER STARTED SUCCESSFULLY");
            System.out.println("=".repeat(70));
            System.out.println("Server binding: " + ServerConfig.HOST + ":" + ServerConfig.PORT);
            System.out.println("(0.0.0.0 means listening on all network interfaces)");
            System.out.println();
            System.out.println("This server's configured IP: " + ServerConfig.SERVER_IP);
            System.out.println("Client Connection Details:");
            System.out.println("  Clients should connect to: tcp://" + ServerConfig.SERVER_IP + ":" + ServerConfig.PORT + "/");
            System.out.println("  (Set in ServerConfig.SERVER_IP and client Config.SERVER_IP)");
            System.out.println();
            System.out.println("Tuple Spaces Available:");
            System.out.println("  - " + TupleSpaces.USERS + " (users space)");
            System.out.println("  - todoLists (todo lists space)");
            System.out.println("  - " + TupleSpaces.TASKS + " (tasks space)");
            System.out.println("  - " + TupleSpaces.REQUESTS + " (client requests)");
            System.out.println("  - " + TupleSpaces.RESPONSES + " (server responses)");
            System.out.println("  - counter (internal counter)");
            System.out.println();
            System.out.println("Example full URI: tcp://" + ServerConfig.SERVER_IP + ":" + ServerConfig.PORT + "/" + TupleSpaces.REQUESTS + "?keep");
            System.out.println("=".repeat(70));
            System.out.println();

            ServerHandlerService service = new ServerHandlerService(todoLists, counter, users, tasks, requests, responses);
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
