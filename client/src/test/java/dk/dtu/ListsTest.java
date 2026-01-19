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
                    "jndi://requests",
                    "jndi://responses",
                    "   "   // invalid (blank)
            );
        });
    }

    @Test
    public void testCreateTodoListRejectsNullName() {
        assertThrows(IllegalArgumentException.class, () -> {
            Lists.createTodoList(
                    "jndi://requests",
                    "jndi://responses",
                    null   // invalid
            );
        });
    }

    @Test
    public void testDeleteTodoListRejectsEmptyId() {
        assertThrows(IllegalArgumentException.class, () -> {
            Lists.deleteTodoList(
                    "jndi://requests",
                    "jndi://responses",
                    ""     // invalid
            );
        });
    }

    @Test
    public void testDeleteTodoListRejectsNullId() {
        assertThrows(IllegalArgumentException.class, () -> {
            Lists.deleteTodoList(
                    "jndi://requests",
                    "jndi://responses",
                    null   // invalid
            );
        });
    }
}
