package dk.dtu;

import dk.dtu.shared.TaskStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TaskStatusTest {

    @Test
    public void testTaskStatusEnumExists() {
        assertNotNull(TaskStatus.class, "TaskStatus enum should exist");
    }

    @Test
    public void testTaskStatusHasNotStartedValue() {
        TaskStatus status = TaskStatus.NOT_STARTED;
        assertNotNull(status, "NOT_STARTED status should exist");
        assertEquals(TaskStatus.NOT_STARTED, status, "Status should be NOT_STARTED");
    }

    @Test
    public void testTaskStatusHasInProgressValue() {
        TaskStatus status = TaskStatus.IN_PROGRESS;
        assertNotNull(status, "IN_PROGRESS status should exist");
        assertEquals(TaskStatus.IN_PROGRESS, status, "Status should be IN_PROGRESS");
    }

    @Test
    public void testTaskStatusHasDelayedValue() {
        TaskStatus status = TaskStatus.DELAYED;
        assertNotNull(status, "DELAYED status should exist");
        assertEquals(TaskStatus.DELAYED, status, "Status should be DELAYED");
    }

    @Test
    public void testTaskStatusHasNeedHelpValue() {
        TaskStatus status = TaskStatus.NEED_HELP;
        assertNotNull(status, "NEED_HELP status should exist");
        assertEquals(TaskStatus.NEED_HELP, status, "Status should be NEED_HELP");
    }

    @Test
    public void testTaskStatusHasDoneValue() {
        TaskStatus status = TaskStatus.DONE;
        assertNotNull(status, "DONE status should exist");
        assertEquals(TaskStatus.DONE, status, "Status should be DONE");
    }

    @Test
    public void testTaskStatusValuesCount() {
        TaskStatus[] values = TaskStatus.values();
        assertEquals(5, values.length, "TaskStatus should have 5 values");
    }

    @Test
    public void testTaskStatusValueOf() {
        TaskStatus status = TaskStatus.valueOf("IN_PROGRESS");
        assertEquals(TaskStatus.IN_PROGRESS, status, "valueOf should return IN_PROGRESS");
    }

    @Test
    public void testTaskStatusEnumComparison() {
        TaskStatus status1 = TaskStatus.DONE;
        TaskStatus status2 = TaskStatus.DONE;
        assertEquals(status1, status2, "Same enum values should be equal");
    }

    @Test
    public void testTaskStatusEnumInequality() {
        TaskStatus status1 = TaskStatus.DONE;
        TaskStatus status2 = TaskStatus.NOT_STARTED;
        assertNotEquals(status1, status2, "Different enum values should not be equal");
    }

}
