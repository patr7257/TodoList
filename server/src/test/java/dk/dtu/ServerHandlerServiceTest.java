package dk.dtu;

import org.junit.jupiter.api.Test;
import org.jspace.*;
import static org.junit.jupiter.api.Assertions.*;

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
        PersistenceService persistenceService = new PersistenceService("./target/test-data");

        ServerHandlerService service = new ServerHandlerService(
                todoLists, counter, users, tasks, requests, responses, notifications, persistenceService);

        assertNotNull(service, "Service should be created");
        assertTrue(service instanceof Runnable, "Service should implement Runnable");
    }

    @Test
    public void testSpaceIntegration() throws Exception {
        Space space = new SequentialSpace();
        space.put("test_key", "test_value");

        Object[] tuple = space.get(new ActualField("test_key"), new ActualField("test_value"));
        assertNotNull(tuple, "Tuple should be retrieved");
        assertEquals("test_value", tuple[1], "Retrieved value should match");
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

        assertNotNull(tuple, "Response should be stored and retrieved");
        assertEquals("OK", tuple[0], "First element should be OK");
        assertEquals("req123", tuple[1], "Second element should be req123");
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

        assertNotNull(tuple, "Notification should be stored");
        assertEquals("list_create", tuple[2], "Operation should be list_create");
    }

    @Test
    public void testTasksSpaceCanStoreTuple() throws Exception {
        Space tasks = new SequentialSpace();
        tasks.put("l1", "task123", "Buy milk", "John", "TODO", "2025-12-12", 5, 0, 0);

        Object[] tuple = tasks.get(
                new ActualField("l1"),
                new ActualField("task123"),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class));

        assertNotNull(tuple, "Task should be stored");
        assertEquals("Buy milk", tuple[2], "Title should be 'Buy milk'");
        assertEquals("TODO", tuple[4], "Status should be 'TODO'");
    }

}
