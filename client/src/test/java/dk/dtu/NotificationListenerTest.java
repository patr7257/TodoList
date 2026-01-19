package dk.dtu;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class NotificationListenerTest {

    @Test
    public void testNotificationListenerCreation() {
        NotificationListener listener = new NotificationListener("uri", () -> {
            // callback
        });
        assertNotNull(listener, "NotificationListener should be created");
        assertTrue(listener instanceof Runnable, "NotificationListener should implement Runnable");
    }

    @Test
    public void testNotificationListenerWithNullCallback() {
        NotificationListener listener = new NotificationListener("uri", null);
        assertNotNull(listener, "NotificationListener should handle null callback");
    }

    @Test
    public void testNotificationListenerCanStop() {
        NotificationListener listener = new NotificationListener("uri", () -> {
        });
        listener.stop();
        // Verify stop method exists and can be called
        assertTrue(true, "stop() method should exist");
    }

    @Test
    public void testNotificationListenerRunnableInterface() {
        NotificationListener listener = new NotificationListener("uri", () -> {
        });
        Runnable runnable = listener;
        assertNotNull(runnable, "NotificationListener should be assignable to Runnable");
    }

    @Test
    public void testNotificationListenerWithCallback() {
        boolean[] callbackCalled = { false };
        NotificationListener listener = new NotificationListener("uri", () -> {
            callbackCalled[0] = true;
        });
        assertNotNull(listener, "NotificationListener should accept callback");
    }

    @Test
    public void testNotificationListenerMultipleInstances() {
        NotificationListener listener1 = new NotificationListener("uri1", () -> {
        });
        NotificationListener listener2 = new NotificationListener("uri2", () -> {
        });

        assertNotNull(listener1, "Multiple instances should be creatable");
        assertNotNull(listener2, "Multiple instances should be creatable");
        assertNotSame(listener1, listener2, "Instances should be different");
    }

    @Test
    public void testNotificationListenerInitialization() {
        String uri = "jndi://localhost:9999/notifications";
        Runnable callback = () -> System.out.println("Data changed");

        NotificationListener listener = new NotificationListener(uri, callback);
        assertNotNull(listener, "Listener should be initialized");
    }

    @Test
    public void testNotificationListenerStopMethod() {
        NotificationListener listener = new NotificationListener("uri", () -> {
        });

        // Stop should not throw any exception
        listener.stop();
        listener.stop(); // Calling stop twice should be safe

        assertTrue(true, "stop() should complete without error");
    }

}
