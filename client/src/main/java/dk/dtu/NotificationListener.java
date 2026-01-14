package dk.dtu;

import javafx.application.Platform;
import org.jspace.FormalField;
import org.jspace.RemoteSpace;

import java.util.HashSet;
import java.util.Set;

/**
 * Simplified notification listener - just signals "something changed" to the UI.
 * SceneNavigator decides what to refresh based on what's currently displayed.
 * This scales much better than having specific handlers for each data type.
 */
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
        try {
            RemoteSpace notifications = new RemoteSpace(notificationsUri);
            System.out.println("Connected to server");
            
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
                System.out.println("Data updated");
                
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
    
    public void stop() {
        running = false;
    }
}
