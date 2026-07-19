package dk.dtu;

import dk.dtu.shared.Config;
import dk.dtu.shared.TupleSpaces;
import javafx.application.Platform;
import org.jspace.ActualField;
import org.jspace.FormalField;
import org.jspace.RemoteSpace;
import java.util.HashSet;
import java.util.Set;

// Listener for notifications from the server
// Calls a single callback when ANY data changes (to refresh current view)
public class NotificationListener implements Runnable {
    
    private final String notificationsUri;
    private final Runnable onDataChanged;  // Single callback - "refresh current view"
    private volatile boolean running = true;
    private final Set<Long> processedTimestamps = new HashSet<>();
    
    /**
     * Create a notification listener.
     * 
     * @param notificationsUri URI of the notifications tuple space
     * @param onDataChanged Callback when ANY data changes (refresh current view)
     */
    public NotificationListener(String notificationsUri, Runnable onDataChanged) {
        this.notificationsUri = notificationsUri;
        this.onDataChanged = onDataChanged;
    }
    
    @Override
    public void run() {
        RemoteSpace notifications = null;
        
        // Try to connect once - no retry loop (connection is already validated before this is called)
        try {
            notifications = new RemoteSpace(notificationsUri);
            System.out.println();
            System.out.println("Connected to server on IP: " + Config.getClientBaseUri() + "\nListening for notifications...");
            
            // Notify server that a new client connected (reuses the pooled
            // request/response connections; the blocking notification get below
            // stays on its OWN dedicated connection, never under the IO lock).
            try {
                String requestId = java.util.UUID.randomUUID().toString();
                Object[] ack;
                synchronized (dk.dtu.methods.Spaces.IO_LOCK) {
                    dk.dtu.methods.Spaces.get(Config.getRequestsUri())
                            .put(TupleSpaces.CMD_CLIENT_CONNECT, requestId, "", "", "", "");
                    // Best-effort: consume response if the server sends one.
                    ack = dk.dtu.methods.Spaces.get(Config.getResponsesUri()).getp(
                            new FormalField(Object.class),
                            new ActualField(requestId),
                            new FormalField(Object.class),
                            new FormalField(Object.class),
                            new FormalField(Object.class),
                            new FormalField(Object.class)
                    );
                }
                if (ack != null) {
                    System.out.println("Server acknowledged client connection.");
                }
            } catch (Exception e) {
                System.err.println("[NotificationListener] Could not notify server about client connection: " + e.getMessage());
            }
            
        } catch (Exception e) {
            System.err.println("[NotificationListener] Cannot connect to notifications space: " + e.getMessage());
            return; // Exit immediately if cannot connect
        }
        
        if (notifications == null) {
            System.err.println("[NotificationListener] Failed to establish connection, exiting.");
            return;
        }
        
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                // BLOCK and wait for ANY notification - check timestamp to avoid infinite loop
                Object[] notification = notifications.get(
                    new FormalField(String.class),
                    new FormalField(Long.class),    // timestamp
                    new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(String.class),
                    new FormalField(String.class)
                );
                
                if (notification == null) continue;
                
                Long timestamp = (Long) notification[1];
                
                // Put update back for other clients
                notifications.put(notification[0], notification[1], notification[2], 
                                 notification[3], notification[4], notification[5]);
                
                // Skip if already processed
                if (processedTimestamps.contains(timestamp)) {
                    continue;
                }
                
                processedTimestamps.add(timestamp);
                
                // Print user-friendly message based on operation type
                String operationType = (String) notification[2];
                String data1 = (String) notification[3];  // listId or taskId
                String data2 = (String) notification[4];  // listName or taskTitle
                
                printFriendlyNotification(operationType, data1, data2);
                
                // Simple: just tell UI "something changed, refresh yourself"
                if (onDataChanged != null) {
                    Platform.runLater(onDataChanged);
                }
                if (processedTimestamps.size() > 1000) {
                    processedTimestamps.clear();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("[NotificationListener] Error: " + e.getMessage());
        }
    }

    // Helper method to print friendly notification messages
    private void printFriendlyNotification(String operationType, String id, String name) {
        switch (operationType) {
            case "list_create":
                System.out.println("List created: " + name);
                break;
            case "task_add":
                System.out.println("Task added: " + name);
                break;
            case "task_status":
                System.out.println("Task status updated: " + name);
                break;
            case "task_delete":
                System.out.println("Task deleted???" + name);
                break;
            case "list_delete":
                System.out.println("List deleted: " + name);
                break;
            default:
                System.out.println("Data updated");
                break;
        }
    }

    public void stop() {
        running = false;
        System.out.println("[NotificationListener] Stopping notification listener...");
    }
    
    public boolean isRunning() {
        return running;
    }
}
