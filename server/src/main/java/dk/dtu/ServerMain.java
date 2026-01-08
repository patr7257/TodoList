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
            System.out.println("Server started.");
            System.out.println("todoLists URI: " + todoListsUri);

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
