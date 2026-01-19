package dk.dtu;

import dk.dtu.methods.Tasks;
import org.junit.Test;

/**
 * Unit tests for Tasks input validation.
 * No backend required.
 */
public class TasksTest {

    @Test(expected = IllegalArgumentException.class)
    public void testAddTaskRejectsNullTitle() throws Exception {
        Tasks.addTask("req", "resp", "list1", null, "2026-01-01", "alice");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddTaskRejectsBlankTitle() throws Exception {
        Tasks.addTask("req", "resp", "list1", "   ", "2026-01-01", "alice");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAssignTaskRejectsNullOwner() throws Exception {
        Tasks.assignTask("req", "resp", "list1", "task1", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAssignTaskRejectsBlankOwner() throws Exception {
        Tasks.assignTask("req", "resp", "list1", "task1", "   ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteTaskRejectsNullTaskId() throws Exception {
        Tasks.deleteTask("req", "resp", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteTaskRejectsBlankTaskId() throws Exception {
        Tasks.deleteTask("req", "resp", "   ");
    }
}

