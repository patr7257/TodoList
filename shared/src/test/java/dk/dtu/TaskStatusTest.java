package dk.dtu;

import dk.dtu.shared.TaskStatus;
import org.junit.Test;
import static org.junit.Assert.*;

public class TaskStatusTest {

    @Test
    public void testTaskStatusEnumExists() {
        assertNotNull("TaskStatus enum should exist", TaskStatus.class);
    }

    @Test
    public void testTaskStatusHasNotStartedValue() {
        TaskStatus status = TaskStatus.NOT_STARTED;
        assertNotNull("NOT_STARTED status should exist", status);
        assertEquals("Status should be NOT_STARTED", TaskStatus.NOT_STARTED, status);
    }

    @Test
    public void testTaskStatusHasInProgressValue() {
        TaskStatus status = TaskStatus.IN_PROGRESS;
        assertNotNull("IN_PROGRESS status should exist", status);
        assertEquals("Status should be IN_PROGRESS", TaskStatus.IN_PROGRESS, status);
    }

    @Test
    public void testTaskStatusHasDelayedValue() {
        TaskStatus status = TaskStatus.DELAYED;
        assertNotNull("DELAYED status should exist", status);
        assertEquals("Status should be DELAYED", TaskStatus.DELAYED, status);
    }

    @Test
    public void testTaskStatusHasNeedHelpValue() {
        TaskStatus status = TaskStatus.NEED_HELP;
        assertNotNull("NEED_HELP status should exist", status);
        assertEquals("Status should be NEED_HELP", TaskStatus.NEED_HELP, status);
    }

    @Test
    public void testTaskStatusHasDoneValue() {
        TaskStatus status = TaskStatus.DONE;
        assertNotNull("DONE status should exist", status);
        assertEquals("Status should be DONE", TaskStatus.DONE, status);
    }

    @Test
    public void testTaskStatusValuesCount() {
        TaskStatus[] values = TaskStatus.values();
        assertEquals("TaskStatus should have 5 values", 5, values.length);
    }

    @Test
    public void testTaskStatusValueOf() {
        TaskStatus status = TaskStatus.valueOf("IN_PROGRESS");
        assertEquals("valueOf should return IN_PROGRESS", TaskStatus.IN_PROGRESS, status);
    }

    @Test
    public void testTaskStatusEnumComparison() {
        TaskStatus status1 = TaskStatus.DONE;
        TaskStatus status2 = TaskStatus.DONE;
        assertEquals("Same enum values should be equal", status1, status2);
    }

    @Test
    public void testTaskStatusEnumInequality() {
        TaskStatus status1 = TaskStatus.DONE;
        TaskStatus status2 = TaskStatus.NOT_STARTED;
        assertNotEquals("Different enum values should not be equal", status1, status2);
    }

}
