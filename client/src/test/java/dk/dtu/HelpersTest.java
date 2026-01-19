package dk.dtu;

import org.junit.Test;

import dk.dtu.methods.Helpers;

import static org.junit.Assert.*;

/**
 * Validates user-friendly string representations and the handling of empty or missing fields of TaskEntry + ListEntry
 */
public class HelpersTest {
    public void testListEntryToString() {
        Helpers.ListEntry e = new Helpers.ListEntry("123", "Inbox");
        assertEquals("123 - Inbox", e.toString());
    }

    @Test
    public void testTaskEntryToStringWithOwnerAndDueDate() {
        Helpers.TaskEntry t = new Helpers.TaskEntry(
                "L1", "T1", "Buy milk", "alice", "OPEN", "2026-02-01"
        );

        String s = t.toString();
        assertTrue(s.contains("Buy milk"));
        assertTrue(s.contains("@alice"));
        assertTrue(s.contains("[OPEN]"));
        assertTrue(s.contains("(due: 2026-02-01)"));
    }

    @Test
    public void testTaskEntryToStringWithoutOwnerAndDueDate() {
        Helpers.TaskEntry t = new Helpers.TaskEntry(
                "L1", "T1", "Buy milk", "   ", "OPEN", ""
        );

        String s = t.toString();
        assertTrue(s.contains("Buy milk"));
        assertFalse("Should not include @ when owner blank", s.contains("@"));
        assertFalse("Should not include due date when blank", s.contains("due:"));
    }
    
}
