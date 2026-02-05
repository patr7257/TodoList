package dk.dtu;

import org.jspace.FormalField;
import org.jspace.SequentialSpace;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceServiceTest {

    private static final String TEST_DATA_DIR = "./target/test-persistence-data";
    private PersistenceService persistenceService;
    private SequentialSpace users;
    private SequentialSpace todoLists;
    private SequentialSpace tasks;

    @BeforeEach
    void setUp() throws IOException {
        // Clean up test directory before each test
        cleanupTestDirectory();
        
        // Create fresh spaces
        users = new SequentialSpace();
        todoLists = new SequentialSpace();
        tasks = new SequentialSpace();
        
        // Create service with test directory
        persistenceService = new PersistenceService(TEST_DATA_DIR);
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up after each test
        cleanupTestDirectory();
    }

    private void cleanupTestDirectory() throws IOException {
        Path testDir = Paths.get(TEST_DATA_DIR);
        if (Files.exists(testDir)) {
            Files.walk(testDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    @Test
    void testDataDirectoryCreation() {
        Path dataDir = persistenceService.getDataDirectory();
        assertTrue(Files.exists(dataDir), "Data directory should be created");
        assertTrue(Files.isDirectory(dataDir), "Should be a directory");
    }

    @Test
    void testNoExistingSessionInitially() {
        assertFalse(persistenceService.hasExistingSession(), "Should have no existing session initially");
    }

    @Test
    void testSaveAndLoadBasicData() throws InterruptedException {
        // Add test data
        users.put("Alice");
        users.put("Bob");
        
        todoLists.put("l1", "Shopping List", 50, "Alice", "", 3, 0, 0, "", "");
        todoLists.put("l2", "Work Tasks", 75, "Bob", "", 7, 0, 1, "", "");
        
        tasks.put("l1", "t1", "Buy milk", "Alice", "NOT_STARTED", "2026-01-20", 2, 0, 0, "", "");
        tasks.put("l1", "t2", "Buy bread", "Bob", "DONE", "2026-01-19", 9, 0, 1, "", "");
        tasks.put("l2", "t3", "Write report", "Alice", "IN_PROGRESS", "", 5, 0, 0, "", "");

        // Save session
        boolean saved = persistenceService.saveSession(users, todoLists, tasks);
        assertTrue(saved, "Save should succeed");
        assertTrue(persistenceService.hasExistingSession(), "Session file should exist after save");

        // Create new spaces for loading
        SequentialSpace loadedUsers = new SequentialSpace();
        SequentialSpace loadedLists = new SequentialSpace();
        SequentialSpace loadedTasks = new SequentialSpace();

        // Load session
        boolean loaded = persistenceService.loadSession(loadedUsers, loadedLists, loadedTasks);
        assertTrue(loaded, "Load should succeed");

        // Verify users
        List<Object[]> userTuples = loadedUsers.queryAll(new FormalField(String.class));
        assertEquals(2, userTuples.size(), "Should have 2 users");
        assertTrue(userTuples.stream().anyMatch(t -> "Alice".equals(t[0])));
        assertTrue(userTuples.stream().anyMatch(t -> "Bob".equals(t[0])));

        // Verify lists
        List<Object[]> listTuples = loadedLists.queryAll(
                new FormalField(String.class),
                new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class));
        assertEquals(2, listTuples.size(), "Should have 2 lists");
        
        Object[] list1 = listTuples.stream()
                .filter(t -> "l1".equals(t[0]))
                .findFirst()
                .orElse(null);
        assertNotNull(list1);
        assertEquals("Shopping List", list1[1]);
        assertEquals(50, list1[2]);
        assertEquals("Alice", list1[3]);
        assertEquals(3, list1[5]);
        assertEquals(0, list1[6]);
        assertEquals(0, list1[7]);

        // Verify tasks
        List<Object[]> taskTuples = loadedTasks.queryAll(
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class));
        assertEquals(3, taskTuples.size(), "Should have 3 tasks");
        
        Object[] task1 = taskTuples.stream()
                .filter(t -> "t1".equals(t[1]))
                .findFirst()
                .orElse(null);
        assertNotNull(task1);
        assertEquals("l1", task1[0]);
        assertEquals("Buy milk", task1[2]);
        assertEquals("Alice", task1[3]);
        assertEquals("NOT_STARTED", task1[4]);
        assertEquals("2026-01-20", task1[5]);
        assertEquals(2, task1[6]);
        assertEquals(0, task1[7]);
        assertEquals(0, task1[8]);
    }

    @Test
    void testSaveEmptySpaces() throws InterruptedException {
        // Save empty spaces
        boolean saved = persistenceService.saveSession(users, todoLists, tasks);
        assertTrue(saved, "Should save empty spaces successfully");

        // Load into new spaces
        SequentialSpace loadedUsers = new SequentialSpace();
        SequentialSpace loadedLists = new SequentialSpace();
        SequentialSpace loadedTasks = new SequentialSpace();

        boolean loaded = persistenceService.loadSession(loadedUsers, loadedLists, loadedTasks);
        assertTrue(loaded, "Should load empty session successfully");

        // Verify empty
        assertEquals(0, loadedUsers.queryAll(new FormalField(String.class)).size());
        assertEquals(0, loadedLists.queryAll(
                new FormalField(String.class),
                new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class)).size());
        assertEquals(0, loadedTasks.queryAll(
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class)).size());
    }

    @Test
    void testMultipleSaves() throws InterruptedException {
        // First save
        users.put("Alice");
        todoLists.put("l1", "List 1", 0, "Alice", "", 1, 0, 0, "", "");
        persistenceService.saveSession(users, todoLists, tasks);

        // Second save with more data
        users.put("Bob");
        todoLists.put("l2", "List 2", 50, "Bob", "", 10, 0, 1, "", "");
        tasks.put("l1", "t1", "Task 1", "Alice", "NOT_STARTED", "", 4, 0, 0, "", "");
        persistenceService.saveSession(users, todoLists, tasks);

        // Load and verify latest state
        SequentialSpace loadedUsers = new SequentialSpace();
        SequentialSpace loadedLists = new SequentialSpace();
        SequentialSpace loadedTasks = new SequentialSpace();
        
        persistenceService.loadSession(loadedUsers, loadedLists, loadedTasks);

        assertEquals(2, loadedUsers.queryAll(new FormalField(String.class)).size());
        assertEquals(2, loadedLists.queryAll(
                new FormalField(String.class),
                new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class)).size());
        assertEquals(1, loadedTasks.queryAll(
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class)).size());
    }

    @Test
    void testDeleteSession() throws InterruptedException {
        // Create and save data
        users.put("Alice");
        persistenceService.saveSession(users, todoLists, tasks);
        assertTrue(persistenceService.hasExistingSession());

        // Delete session
        boolean deleted = persistenceService.deleteSession();
        assertTrue(deleted, "Delete should succeed");
        assertFalse(persistenceService.hasExistingSession(), "Session should no longer exist");

        // Try to delete again
        boolean deletedAgain = persistenceService.deleteSession();
        assertFalse(deletedAgain, "Second delete should return false");
    }

    @Test
    void testLoadNonExistentSession() {
        SequentialSpace loadedUsers = new SequentialSpace();
        SequentialSpace loadedLists = new SequentialSpace();
        SequentialSpace loadedTasks = new SequentialSpace();

        boolean loaded = persistenceService.loadSession(loadedUsers, loadedLists, loadedTasks);
        assertFalse(loaded, "Loading non-existent session should return false");
    }

    @Test
    void testTasksWithEmptyFields() throws InterruptedException {
        // Add tasks with empty assignees and due dates
        tasks.put("l1", "t1", "Unassigned task", "", "NOT_STARTED", "", 5, 0, 0, "", "");
        tasks.put("l1", "t2", "No due date", "Alice", "IN_PROGRESS", "", 5, 0, 1, "", "");
        
        persistenceService.saveSession(users, todoLists, tasks);

        // Load and verify
        SequentialSpace loadedTasks = new SequentialSpace();
        persistenceService.loadSession(new SequentialSpace(), new SequentialSpace(), loadedTasks);

        List<Object[]> taskTuples = loadedTasks.queryAll(
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class));
        
        assertEquals(2, taskTuples.size());
        
        Object[] task1 = taskTuples.stream()
                .filter(t -> "t1".equals(t[1]))
                .findFirst()
                .orElse(null);
        assertNotNull(task1);
        assertEquals("", task1[3]); // Empty assignee
        assertEquals("", task1[5]); // Empty due date
    }

    @Test
    void testSpecialCharactersInData() throws InterruptedException {
        // Test with special characters
        users.put("User_With-Special.Chars@123");
        todoLists.put("l1", "List with émojis 🎉 and quotes \"test\"", 33, "User_With-Special.Chars@123", "", 6, 0, 0, "", "");
        tasks.put("l1", "t1", "Task with\nnewlines\tand\ttabs", "User_With-Special.Chars@123", "DONE", "", 6, 0, 0, "", "");

        persistenceService.saveSession(users, todoLists, tasks);

        // Load and verify
        SequentialSpace loadedUsers = new SequentialSpace();
        SequentialSpace loadedLists = new SequentialSpace();
        SequentialSpace loadedTasks = new SequentialSpace();
        
        persistenceService.loadSession(loadedUsers, loadedLists, loadedTasks);

        // Verify special characters preserved
        List<Object[]> userTuples = loadedUsers.queryAll(new FormalField(String.class));
        assertEquals("User_With-Special.Chars@123", userTuples.get(0)[0]);

        List<Object[]> listTuples = loadedLists.queryAll(
                new FormalField(String.class),
                new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class));
        assertTrue(((String) listTuples.get(0)[1]).contains("émojis"));
        assertTrue(((String) listTuples.get(0)[1]).contains("🎉"));

        List<Object[]> taskTuples = loadedTasks.queryAll(
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
                new FormalField(String.class),
            new FormalField(String.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(Integer.class),
            new FormalField(String.class),
            new FormalField(String.class));
        assertTrue(((String) taskTuples.get(0)[2]).contains("\n"));
        assertTrue(((String) taskTuples.get(0)[2]).contains("\t"));
    }

    @Test
    void testCustomDataDirectory() {
        String customDir = "./target/custom-test-dir";
        PersistenceService customService = new PersistenceService(customDir);
        
        assertEquals(Paths.get(customDir).toAbsolutePath(), 
                     customService.getDataDirectory().toAbsolutePath());
        assertTrue(Files.exists(customService.getDataDirectory()));
        
        // Cleanup
        try {
            Files.walk(Paths.get(customDir))
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        } catch (IOException e) {
            // Ignore cleanup errors in test
        }
    }
}
