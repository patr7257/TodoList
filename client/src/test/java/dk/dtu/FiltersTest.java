package dk.dtu;

import org.junit.jupiter.api.Test;

import dk.dtu.methods.Filters;
import dk.dtu.methods.Helpers;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure, JavaFX-free tests for the "only mine" filter predicates: id-based
 * matching for lists (ownerId) and tasks (assigneeId), and the fail-open
 * behavior when the signed-in user id is not known yet.
 */
public class FiltersTest {

    @Test
    public void testMatchesOnlyMineTrueWhenIdsMatch() {
        assertTrue(Filters.matchesOnlyMine("u1", "u1"));
    }

    @Test
    public void testMatchesOnlyMineFalseWhenIdsDiffer() {
        assertFalse(Filters.matchesOnlyMine("u2", "u1"));
    }

    @Test
    public void testMatchesOnlyMineFalseWhenEntityIdIsNullButCurrentUserIsKnown() {
        assertFalse(Filters.matchesOnlyMine(null, "u1"),
                "an unowned entity is not 'mine' once a signed-in user is known");
    }

    @Test
    public void testMatchesOnlyMineFailsOpenWhenCurrentUserIsNull() {
        assertTrue(Filters.matchesOnlyMine("u1", null),
                "no known signed-in user -> fail open (show everything), never hide all rows");
    }

    @Test
    public void testMatchesOnlyMineFailsOpenWhenCurrentUserIsBlank() {
        assertTrue(Filters.matchesOnlyMine("u1", "   "));
    }

    @Test
    public void testMatchesOnlyMineFailsOpenEvenWhenEntityIdIsAlsoNull() {
        assertTrue(Filters.matchesOnlyMine(null, null));
    }

    // -- onlyMyLists / onlyMyTasks predicate factories --------------------------

    @Test
    public void testOnlyMyListsMatchesByOwnerId() {
        Helpers.ListEntry mine = new Helpers.ListEntry("l1", "Mine", "Alice", "", 0, 0, 0, "", "", 0, 0, 0, "u1");
        Helpers.ListEntry notMine = new Helpers.ListEntry("l2", "Not mine", "Bob", "", 0, 0, 0, "", "", 0, 0, 0, "u2");

        Predicate<Helpers.ListEntry> predicate = Filters.onlyMyLists("u1");
        assertTrue(predicate.test(mine));
        assertFalse(predicate.test(notMine));
    }

    @Test
    public void testOnlyMyListsFailsOpenWhenCurrentUserUnknown() {
        Helpers.ListEntry anyList = new Helpers.ListEntry("l1", "Any", "Bob", "", 0, 0, 0, "", "", 0, 0, 0, "u2");
        assertTrue(Filters.onlyMyLists(null).test(anyList));
        assertTrue(Filters.onlyMyLists(null).test(null));
    }

    @Test
    public void testOnlyMyTasksMatchesByAssigneeId() {
        Helpers.TaskEntry mine = new Helpers.TaskEntry("l1", "t1", "Buy milk", "Alice", "NOT_STARTED", "", 0, 0, 0, "", "", "u1");
        Helpers.TaskEntry notMine = new Helpers.TaskEntry("l1", "t2", "Buy eggs", "Bob", "NOT_STARTED", "", 0, 0, 0, "", "", "u2");

        Predicate<Helpers.TaskEntry> predicate = Filters.onlyMyTasks("u1");
        assertTrue(predicate.test(mine));
        assertFalse(predicate.test(notMine));
    }

    @Test
    public void testOnlyMyTasksFailsOpenWhenCurrentUserUnknown() {
        Helpers.TaskEntry anyTask = new Helpers.TaskEntry("l1", "t1", "Buy milk", "Bob", "NOT_STARTED", "", 0, 0, 0, "", "", "u2");
        assertTrue(Filters.onlyMyTasks("").test(anyTask));
        assertTrue(Filters.onlyMyTasks("").test(null));
    }
}
