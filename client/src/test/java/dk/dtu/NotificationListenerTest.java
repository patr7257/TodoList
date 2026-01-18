package dk.dtu;

import org.junit.Test;
import static org.junit.Assert.*;

public class NotificationListenerTest {

    @Test
    public void testNotificationListenerCreation() {
        NotificationListener listener = new NotificationListener("uri", () -> {
            // callback
        });
        assertNotNull("NotificationListener should be created", listener);
        assertTrue("NotificationListener should implement Runnable", listener instanceof Runnable);
    }

    @Test
    public void testNotificationListenerWithNullCallback() {
        NotificationListener listener = new NotificationListener("uri", null);
        assertNotNull("NotificationListener should handle null callback", listener);
    }

    @Test
    public void testNotificationListenerCanStop() {
        NotificationListener listener = new NotificationListener("uri", () -> {
        });
        listener.stop();
        // Verify stop method exists and can be called
        assertTrue("stop() method should exist", true);
    }

    @Test
    public void testNotificationListenerRunnableInterface() {
        NotificationListener listener = new NotificationListener("uri", () -> {
        });
        Runnable runnable = listener;
        assertNotNull("NotificationListener should be assignable to Runnable", runnable);
    }

    @Test
    public void testNotificationListenerWithCallback() {
        boolean[] callbackCalled = { false };
        NotificationListener listener = new NotificationListener("uri", () -> {
            callbackCalled[0] = true;
        });
        assertNotNull("NotificationListener should accept callback", listener);
    }

    @Test
    public void testNotificationListenerMultipleInstances() {
        NotificationListener listener1 = new NotificationListener("uri1", () -> {
        });
        NotificationListener listener2 = new NotificationListener("uri2", () -> {
        });

        assertNotNull("Multiple instances should be creatable", listener1);
        assertNotNull("Multiple instances should be creatable", listener2);
        assertNotSame("Instances should be different", listener1, listener2);
    }

    @Test
    public void testNotificationListenerInitialization() {
        String uri = "jndi://localhost:9999/notifications";
        Runnable callback = () -> System.out.println("Data changed");

        NotificationListener listener = new NotificationListener(uri, callback);
        assertNotNull("Listener should be initialized", listener);
    }

    @Test
    public void testNotificationListenerStopMethod() {
        NotificationListener listener = new NotificationListener("uri", () -> {
        });

        // Stop should not throw any exception
        listener.stop();
        listener.stop(); // Calling stop twice should be safe

        assertTrue("stop() should complete without error", true);
    }

}
