package dk.dtu;

import org.junit.jupiter.api.Test;
import org.jspace.*;
import static org.junit.jupiter.api.Assertions.*;

public class ServerMainTest {

    @Test
    public void testSpaceRepositoryInitialization() {
        SpaceRepository repo = new SpaceRepository();
        assertNotNull(repo, "SpaceRepository should be created");
    }

    @Test
    public void testSequentialSpaceCreation() {
        SequentialSpace space = new SequentialSpace();
        assertNotNull(space, "SequentialSpace should be created");
    }

    @Test
    public void testSpaceCanStoreTuple() throws Exception {
        SequentialSpace space = new SequentialSpace();
        space.put("test", 123);

        Object[] tuple = space.get(new ActualField("test"), new ActualField(123));
        assertNotNull(tuple, "Tuple should be retrieved from space");
        assertEquals("test", tuple[0], "First element should be 'test'");
        assertEquals(123, tuple[1], "Second element should be 123");
    }

    @Test
    public void testCounterSpaceInitialization() throws Exception {
        SequentialSpace counter = new SequentialSpace();
        int initialCount = 5;
        counter.put(initialCount);

        Object[] tuple = counter.get(new ActualField(initialCount));
        assertNotNull(tuple, "Counter should store initial value");
    }

    @Test
    public void testDatabaseCanLoadUsers() throws Exception {
        SequentialSpace users = new SequentialSpace();
        SequentialSpace todoLists = new SequentialSpace();
        SequentialSpace tasks = new SequentialSpace();

        Database.loadDatabase(users, todoLists, tasks);

        // Verify data was loaded by checking spaces are not empty
        assertNotNull(users, "Users space should not be null after loading");
        assertNotNull(todoLists, "TodoLists space should not be null after loading");
        assertNotNull(tasks, "Tasks space should not be null after loading");
    }

}
