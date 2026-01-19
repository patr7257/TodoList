package dk.dtu;

import org.junit.Test;

import dk.dtu.methods.Lists;

/**
 * Unit tests for Lists input validation.
 */
public class ListsTest {
    
    @Test(expected = IllegalArgumentException.class)
    public void testCreateTodoListRejectsEmptyName() throws Exception {
        Lists.createTodoList(
                "jndi://requests",
                "jndi://responses",
                "   "   // invalid (blank)
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateTodoListRejectsNullName() throws Exception {
        Lists.createTodoList(
                "jndi://requests",
                "jndi://responses",
                null   // invalid
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteTodoListRejectsEmptyId() throws Exception {
        Lists.deleteTodoList(
                "jndi://requests",
                "jndi://responses",
                ""     // invalid
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteTodoListRejectsNullId() throws Exception {
        Lists.deleteTodoList(
                "jndi://requests",
                "jndi://responses",
                null   // invalid
        );
    }
}
