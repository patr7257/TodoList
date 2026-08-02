package dk.dtu;

import org.junit.jupiter.api.Test;

import dk.dtu.methods.Helpers;
import dk.dtu.net.ApiModels.ItemDto;
import dk.dtu.net.ApiModels.ListDto;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the UI row string helpers plus the API-DTO -> UI mapping and the
 * date conversions between the API's ISO instants and the desktop's yyyy-MM-dd.
 */
public class HelpersTest {

    @Test
    public void testListEntryToString() {
        Helpers.ListEntry e = new Helpers.ListEntry("123", "Inbox", "", "", 5, 0, 0, "", "", 0, 0, 0, null);
        assertEquals("123 - Inbox", e.toString());
    }

    @Test
    public void testTaskEntryToStringWithOwnerAndDueDate() {
        Helpers.TaskEntry t = new Helpers.TaskEntry(
            "L1", "T1", "Buy milk", "alice", "OPEN", "2026-02-01", 5, 0, 0, "", "", "u1"
        );

        String s = t.toString();
        assertTrue(s.contains("Buy milk"));
        assertTrue(s.contains("alice"));
        assertTrue(s.contains("[OPEN]"));
        assertTrue(s.contains("(due: 2026-02-01)"));
    }

    @Test
    public void testTaskEntryToStringWithoutOwnerAndDueDate() {
        Helpers.TaskEntry t = new Helpers.TaskEntry(
            "L1", "T1", "Buy milk", "   ", "OPEN", "", 5, 0, 0, "", "", null
        );
        String s = t.toString();
        assertTrue(s.contains("Buy milk"));
        assertFalse(s.contains("@"), "Should not include @ when owner blank");
        assertFalse(s.contains("due:"), "Should not include due date when blank");
    }

    // -- date conversions ------------------------------------------------------

    @Test
    public void testIsoInstantToDate() {
        assertEquals("2026-07-20", Helpers.isoInstantToDate("2026-07-20T00:00:00Z"));
        assertEquals("2026-07-20", Helpers.isoInstantToDate("2026-07-20T23:59:59Z"));
        assertEquals("", Helpers.isoInstantToDate(null));
        assertEquals("", Helpers.isoInstantToDate(""));
    }

    @Test
    public void testDateToIsoInstant() {
        assertNull(Helpers.dateToIsoInstant(""), "blank date maps to null (no due date)");
        assertNull(Helpers.dateToIsoInstant(null));
        String iso = Helpers.dateToIsoInstant("2026-07-20");
        assertEquals("2026-07-20T00:00:00Z", iso);
        // round-trips back to the same date
        assertEquals("2026-07-20", Helpers.isoInstantToDate(iso));
    }

    // -- DTO mapping -----------------------------------------------------------

    @Test
    public void testToTaskEntryUsesAssigneeNameAndDefaults() {
        ItemDto it = new ItemDto("i1", "l1", "Buy milk", "desc", false, "IN_PROGRESS",
                null, "2026-07-20T00:00:00Z", "Kitchen", "u2", 3, "u1",
                null, null, null, "Bob");
        Helpers.TaskEntry t = Helpers.toTaskEntry(it);

        assertEquals("Buy milk", t.title);
        assertEquals("Bob", t.owner, "owner column shows the resolved assignee name");
        assertEquals("IN_PROGRESS", t.status);
        assertEquals("2026-07-20", t.dueDate);
        assertEquals("Kitchen", t.location);
        assertEquals(3, t.orderIndex);
        assertEquals(dk.dtu.shared.Defaults.PRIORITY, t.priority, "null priority falls back to default");
    }

    @Test
    public void testToListEntryCountsTasksAndOverdue() {
        String yesterday = LocalDate.now().minusDays(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant().atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT);
        String tomorrow = LocalDate.now().plusDays(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant().atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT);

        ItemDto overdue = new ItemDto("i1", "l1", "late", null, false, "NOT_STARTED",
                null, yesterday, null, null, 0, null, null, null, null, null);
        ItemDto future = new ItemDto("i2", "l1", "soon", null, false, "NOT_STARTED",
                null, tomorrow, null, null, 1, null, null, null, null, null);
        ItemDto doneLate = new ItemDto("i3", "l1", "done", null, true, "DONE",
                null, yesterday, null, null, 2, null, null, null, null, null);

        ListDto list = new ListDto("l1", "Inbox", 0, null, null, null, null, null, null,
                null, 33, List.of(overdue, future, doneLate), null, null);

        Helpers.ListEntry e = Helpers.toListEntry(list);
        assertEquals("Inbox", e.name);
        assertEquals(33, e.completionPercentage);
        assertEquals(3, e.taskCount);
        assertEquals(1, e.overdueTaskCount, "only the not-done, past-due item is overdue");
    }

    // -- owner display precedence (ownerName wins over legacy owner text) ------

    @Test
    public void testToListEntryPrefersOwnerNameOverLegacyOwnerText() {
        ListDto list = new ListDto("l1", "Inbox", 0, null, "Stale Legacy Text", null, null, null, null,
                null, null, List.of(), "u1", "Resolved Name");
        Helpers.ListEntry e = Helpers.toListEntry(list);
        assertEquals("Resolved Name", e.owner);
        assertEquals("u1", e.ownerId);
    }

    @Test
    public void testToListEntryFallsBackToLegacyOwnerWhenOwnerNameIsNullOrBlank() {
        ListDto withNullName = new ListDto("l1", "Inbox", 0, null, "Alice", null, null, null, null,
                null, null, List.of(), "u1", null);
        assertEquals("Alice", Helpers.toListEntry(withNullName).owner);

        ListDto withBlankName = new ListDto("l1", "Inbox", 0, null, "Alice", null, null, null, null,
                null, null, List.of(), "u1", "   ");
        assertEquals("Alice", Helpers.toListEntry(withBlankName).owner);
    }

    @Test
    public void testToListEntryCarriesOwnerIdThrough() {
        ListDto list = new ListDto("l1", "Inbox", 0, null, null, null, null, null, null,
                null, null, List.of(), "owner-123", "Someone");
        assertEquals("owner-123", Helpers.toListEntry(list).ownerId);
    }

    @Test
    public void testToTaskEntryCarriesAssigneeIdThrough() {
        ItemDto it = new ItemDto("i1", "l1", "Buy milk", "desc", false, "IN_PROGRESS",
                null, null, "Kitchen", "u2", 3, "u1",
                null, null, null, "Bob");
        assertEquals("u2", Helpers.toTaskEntry(it).assigneeId);
    }
}
