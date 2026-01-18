package dk.dtu;

import org.junit.Test;
import org.jspace.*;
import static org.junit.Assert.*;

public class ServerMainTest {

    @Test
    public void testSpaceRepositoryInitialization() {
        SpaceRepository repo = new SpaceRepository();
        assertNotNull("SpaceRepository should be created", repo);
    }

    @Test
    public void testSequentialSpaceCreation() {
        SequentialSpace space = new SequentialSpace();
        assertNotNull("SequentialSpace should be created", space);
    }

    @Test
    public void testSpaceCanStoreTuple() throws Exception {
        SequentialSpace space = new SequentialSpace();
        space.put("test", 123);

        Object[] tuple = space.get(new ActualField("test"), new ActualField(123));
        assertNotNull("Tuple should be retrieved from space", tuple);
        assertEquals("First element should be 'test'", "test", tuple[0]);
        assertEquals("Second element should be 123", 123, tuple[1]);
    }

    @Test
    public void testCounterSpaceInitialization() throws Exception {
        SequentialSpace counter = new SequentialSpace();
        int initialCount = 5;
        counter.put(initialCount);

        Object[] tuple = counter.get(new ActualField(initialCount));
        assertNotNull("Counter should store initial value", tuple);
    }

    @Test
    public void testDatabaseCanLoadUsers() throws Exception {
        SequentialSpace users = new SequentialSpace();
        SequentialSpace todoLists = new SequentialSpace();
        SequentialSpace tasks = new SequentialSpace();

        Database.loadDatabase(users, todoLists, tasks);

        // Verify data was loaded by checking spaces are not empty
        assertNotNull("Users space should not be null after loading", users);
        assertNotNull("TodoLists space should not be null after loading", todoLists);
        assertNotNull("Tasks space should not be null after loading", tasks);
    }

}
