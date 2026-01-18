package dk.dtu;

import org.junit.Test;
import org.jspace.*;
import static org.junit.Assert.*;

public class ServerHandlerServiceTest {

    @Test
    public void testServerHandlerServiceInitialization() {
        Space todoLists = new SequentialSpace();
        Space counter = new SequentialSpace();
        Space users = new SequentialSpace();
        Space tasks = new SequentialSpace();
        Space requests = new SequentialSpace();
        Space responses = new SequentialSpace();
        Space notifications = new SequentialSpace();

        ServerHandlerService service = new ServerHandlerService(
                todoLists, counter, users, tasks, requests, responses, notifications);

        assertNotNull("Service should be created", service);
        assertTrue("Service should implement Runnable", service instanceof Runnable);
    }

    @Test
    public void testSpaceIntegration() throws Exception {
        Space space = new SequentialSpace();
        space.put("test_key", "test_value");

        Object[] tuple = space.get(new ActualField("test_key"), new ActualField("test_value"));
        assertNotNull("Tuple should be retrieved", tuple);
        assertEquals("Retrieved value should match", "test_value", tuple[1]);
    }

    @Test
    public void testResponseSpaceCanStoreTuple() throws Exception {
        Space responses = new SequentialSpace();
        responses.put("OK", "req123", "result", "", "", "");

        Object[] tuple = responses.get(
                new ActualField("OK"),
                new ActualField("req123"),
                new FormalField(Object.class),
                new FormalField(Object.class),
                new FormalField(Object.class),
                new FormalField(Object.class));

        assertNotNull("Response should be stored and retrieved", tuple);
        assertEquals("First element should be OK", "OK", tuple[0]);
        assertEquals("Second element should be req123", "req123", tuple[1]);
    }

    @Test
    public void testNotificationSpaceCanStoreBroadcast() throws Exception {
        Space notifications = new SequentialSpace();
        long timestamp = System.currentTimeMillis();
        notifications.put("NOTIFY", timestamp, "list_create", "l1", "My List", "");

        Object[] tuple = notifications.get(
                new ActualField("NOTIFY"),
                new FormalField(Long.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class));

        assertNotNull("Notification should be stored", tuple);
        assertEquals("Operation should be list_create", "list_create", tuple[2]);
    }

    @Test
    public void testTasksSpaceCanStoreTuple() throws Exception {
        Space tasks = new SequentialSpace();
        tasks.put("l1", "task123", "Buy milk", "John", "TODO", "2025-12-12");

        Object[] tuple = tasks.get(
                new ActualField("l1"),
                new ActualField("task123"),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class));

        assertNotNull("Task should be stored", tuple);
        assertEquals("Title should be 'Buy milk'", "Buy milk", tuple[2]);
        assertEquals("Status should be 'TODO'", "TODO", tuple[4]);
    }

}
