package dk.dtu;

import dk.dtu.methods.Dashboard;
import dk.dtu.methods.Dashboard.DashboardStats;
import dk.dtu.methods.Helpers;
import dk.dtu.net.ApiModels.ItemDto;
import dk.dtu.net.ApiModels.ListDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for {@link Dashboard}'s 6 live stats against hand-built fixture
 * data (acceptance criterion 6 of issue #46). "Now" is always injected so
 * these are fully deterministic.
 *
 * <p>NOTE ON A SEAM WITH THE PARALLEL #45 BRANCH: {@code ListDto} is gaining
 * two trailing components ({@code ownerId}, {@code ownerName}) on the
 * "feat/owner-fk-and-dashboard" branch this branch is based on / will merge
 * with. Every positional {@code ListDto} construction below already carries
 * the two trailing {@code null}s, so this file compiles once merged. Until
 * then (in this worktree alone) it does NOT compile, which is the expected,
 * called-out pre-merge seam from the brief, not a bug in this file.
 */
class DashboardStatsTest {

    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");
    private static final String USER_1 = "u1";

    /** Builds a list with the fields Dashboard actually reads; the rest are irrelevant filler. */
    private static ListDto list(String id, List<ItemDto> items) {
        return new ListDto(id, "List " + id, 0, "2026-01-01T00:00:00Z", null, null, null, null, null, null,
                null, items, null, null);
    }

    private static ItemDto item(String id, boolean done, String status, String dueAt, String assigneeId, String createdAt) {
        return new ItemDto(id, "l", "text-" + id, null, done, status, null, dueAt, null, assigneeId, 0, null,
                createdAt, null, null, null);
    }

    @Test
    void computesAllSixStatsAgainstAHandBuiltFixture() {
        ItemDto notStartedRecent = item("i1", false, "NOT_STARTED", null, "u1", "2026-07-30T00:00:00Z");
        ItemDto doneOld = item("i2", true, "DONE", "2026-07-01T00:00:00Z", "u2", "2026-06-01T00:00:00Z");
        ItemDto overdueRecent = item("i3", false, "IN_PROGRESS", "2026-07-01T00:00:00Z", "u1", "2026-08-01T00:00:00Z");
        ItemDto delayedFutureDue = item("i4", false, "DELAYED", "2026-09-01T00:00:00Z", null, "2026-07-20T00:00:00Z");
        // Unknown/blank status and unparseable dates must be skipped, never thrown on.
        ItemDto malformed = item("i5", false, null, "not-a-date", "u1", "bad-date");

        List<ListDto> lists = List.of(
                list("l1", List.of(notStartedRecent, doneOld, overdueRecent)),
                list("l2", List.of(delayedFutureDue, malformed)));

        DashboardStats stats = Dashboard.compute(lists, USER_1, NOW, 7);

        assertEquals(2, stats.totalLists(), "total lists");
        assertEquals(5, stats.totalTasks(), "total tasks");
        assertEquals(1, stats.doneTasks(), "done tasks (only i2)");
        // (0 + 100 + 50 + 50 + 0) / 5 = 40, summed over ALL items (not averaged per list).
        assertEquals(40, stats.completionPercent(), "completion percent");
        assertEquals(1, stats.overdueTasks(), "overdue: only i3 (past due, not done)");
        assertEquals(3, stats.myOpenTasks(), "my open tasks: i1, i3, i5 assigned to u1 and not done");
        assertEquals(2, stats.addedRecently(), "added in last 7 days: i1 and i3");

        Map<String, Integer> mix = stats.statusMix();
        assertEquals(2, mix.get("NOT_STARTED"), "i1 explicit + i5 unknown/null bucketed here");
        assertEquals(1, mix.get("IN_PROGRESS"));
        assertEquals(1, mix.get("DELAYED"));
        assertEquals(0, mix.get("NEED_HELP"));
        assertEquals(1, mix.get("DONE"));
        int mixSum = mix.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(stats.totalTasks(), mixSum, "status mix must sum to the total task count");
    }

    @Test
    void overdueCountMatchesTheListsTablesExistingRuleExactly() {
        // Dashboard.compute is instructed to reuse Helpers.isoInstantToDate + the
        // same comparison Helpers.toListEntry already uses for the lists table's
        // Overdue column, so both must agree on the SAME "today". Helpers.toListEntry
        // uses LocalDate.now() internally (not injectable), so this test injects
        // Instant.now() into Dashboard.compute to compare them at the same instant.
        Instant now = Instant.now();

        ItemDto overdueNotDone = item("a", false, "IN_PROGRESS", "2020-01-01T00:00:00Z", null, "2020-01-01T00:00:00Z");
        ItemDto overdueButDone = item("b", true, "DONE", "2020-01-01T00:00:00Z", null, "2020-01-01T00:00:00Z");
        ItemDto futureDue = item("c", false, "NOT_STARTED", "2099-01-01T00:00:00Z", null, "2020-01-01T00:00:00Z");
        ItemDto noDue = item("d", false, "NOT_STARTED", null, null, "2020-01-01T00:00:00Z");

        ListDto l = list("l1", List.of(overdueNotDone, overdueButDone, futureDue, noDue));

        DashboardStats stats = Dashboard.compute(List.of(l), null, now);
        int viaHelpers = Helpers.toListEntry(l).overdueTaskCount;

        assertEquals(1, viaHelpers, "sanity: exactly one item should be overdue per the existing lists-table rule");
        assertEquals(viaHelpers, stats.overdueTasks(),
                "the dashboard's overdue count must equal the sum of the lists table's Overdue column");
    }

    @Test
    void neverThrowsOnNullOrEmptyInput() {
        assertDoesNotThrow(() -> {
            DashboardStats stats = Dashboard.compute(null, null, null);
            assertEquals(0, stats.totalLists());
            assertEquals(0, stats.totalTasks());
            assertEquals(0, stats.completionPercent());
        });

        assertDoesNotThrow(() -> {
            // A list with a null items collection must be skipped, not thrown on.
            ListDto listWithNullItems = list("l1", null);
            DashboardStats stats = Dashboard.compute(List.of(listWithNullItems), "someone", Instant.now());
            assertEquals(1, stats.totalLists());
            assertEquals(0, stats.totalTasks());
        });

        assertDoesNotThrow(() -> {
            // A null entry inside the items list must be skipped, not thrown on.
            java.util.List<ItemDto> itemsWithNull = new java.util.ArrayList<>();
            itemsWithNull.add(item("ok", false, "NOT_STARTED", null, null, null));
            itemsWithNull.add(null);
            ListDto l = list("l1", itemsWithNull);
            DashboardStats stats = Dashboard.compute(List.of(l), null, Instant.now());
            assertEquals(1, stats.totalTasks());
        });
    }

    @Test
    void emptyStateYieldsAllZeroStatsNotNulls() {
        DashboardStats stats = Dashboard.compute(List.of(), "u1", NOW);
        assertNotNull(stats.statusMix());
        assertEquals(0, stats.totalLists());
        assertEquals(0, stats.totalTasks());
        assertEquals(0, stats.doneTasks());
        assertEquals(0, stats.completionPercent());
        assertEquals(0, stats.overdueTasks());
        assertEquals(0, stats.myOpenTasks());
        assertEquals(0, stats.addedRecently());
    }
}
