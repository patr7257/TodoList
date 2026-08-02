package dk.dtu;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import dk.dtu.methods.Lists;

/**
 * Unit tests for Lists input validation.
 */
public class ListsTest {

    @Test
    public void testCreateTodoListRejectsEmptyName() {
        assertThrows(IllegalArgumentException.class, () -> {
            Lists.createTodoList(
                    "   ",  // invalid (blank)
                    "alice"
            );
        });
    }

    @Test
    public void testCreateTodoListRejectsNullName() {
        assertThrows(IllegalArgumentException.class, () -> {
            Lists.createTodoList(
                    null,   // invalid
                    "alice"
            );
        });
    }

    @Test
    public void testDeleteTodoListRejectsEmptyId() {
        assertThrows(IllegalArgumentException.class, () -> {
            Lists.deleteTodoList(
                    ""     // invalid
            );
        });
    }

    @Test
    public void testDeleteTodoListRejectsNullId() {
        assertThrows(IllegalArgumentException.class, () -> {
            Lists.deleteTodoList(
                    null   // invalid
            );
        });
    }

    // The desktop-superset setters guard their inputs before any network call.

    @Test
    public void testSetListOwnerIdRejectsBlankListId() {
        assertThrows(IllegalArgumentException.class, () -> {
            Lists.setListOwnerId("   ", "u1");
        });
    }

    @Test
    public void testSetListOwnerIdRejectsNullListId() {
        assertThrows(IllegalArgumentException.class, () -> {
            Lists.setListOwnerId(null, "u1");
        });
    }

    @Test
    public void testSetListPriorityRejectsBlankId() {
        assertThrows(IllegalArgumentException.class, () -> {
            Lists.setListPriority("   ", 1);
        });
    }

    @Test
    public void testSetListYearRejectsNullId() {
        assertThrows(IllegalArgumentException.class, () -> {
            Lists.setListYear(null, 2027);
        });
    }

    @Test
    public void testSetListDescriptionRejectsBlankId() {
        assertThrows(IllegalArgumentException.class, () -> {
            Lists.setListDescription("", "some notes");
        });
    }

}
