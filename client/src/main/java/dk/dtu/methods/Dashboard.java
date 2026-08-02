package dk.dtu.methods;

import dk.dtu.net.ApiModels.ItemDto;
import dk.dtu.net.ApiModels.ListDto;
import dk.dtu.shared.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure, static derivation of the dashboard's 6 live stats (plus an optional
 * status mix) from the same {@code GET /state} payload the lists/tasks views
 * already use: no new read endpoint. Deliberately a plain data-in/data-out
 * class (no JavaFX, no network) so it is trivially unit-testable with
 * hand-built fixtures; the "now" instant is an explicit parameter (never
 * {@code Instant.now()} internally) so tests are deterministic.
 *
 * <p>Every computation here is defensive: a null list, a null/empty items
 * list, a null item, or an unparseable date/status never throws: the item is
 * simply excluded from that count. This backs the dashboard's fail-safe
 * requirement (a hand-edited or malformed row must never blank a tile or kill
 * the screen), and matches {@link Helpers#toListEntry(ListDto)}'s existing
 * "skip on failure" pattern for the same fields.
 */
public final class Dashboard {

    /** Default window for the "added recently" stat. */
    public static final int DEFAULT_RECENT_DAYS = 7;

    private static final List<String> KNOWN_STATUSES =
            List.of("NOT_STARTED", "IN_PROGRESS", "DELAYED", "NEED_HELP", "DONE");

    private Dashboard() {
    }

    /**
     * The 6 live stats plus a per-status mix. All counts are non-negative and
     * never null; a computation that cannot produce a real value (empty input)
     * yields 0, so the UI never has to special-case "unknown" here (the UI's own
     * per-tile guard exists for defense in depth against unforeseen exceptions).
     */
    public record DashboardStats(
            int totalLists,
            int totalTasks,
            int doneTasks,
            int completionPercent,
            int overdueTasks,
            int myOpenTasks,
            int addedRecently,
            Map<String, Integer> statusMix) {
    }

    /** {@link #compute(List, String, Instant, int)} using {@link #DEFAULT_RECENT_DAYS}. */
    public static DashboardStats compute(List<ListDto> lists, String currentUserId, Instant now) {
        return compute(lists, currentUserId, now, DEFAULT_RECENT_DAYS);
    }

    /**
     * Computes every stat in one pass over the flattened item set.
     *
     * @param lists          the state payload's lists (with nested items); null-safe
     * @param currentUserId  the signed-in user's id, for "my open tasks"; null-safe
     * @param now            the "current" instant, injected for deterministic tests
     * @param recentDaysBack window size for the "added recently" stat
     */
    public static DashboardStats compute(List<ListDto> lists, String currentUserId, Instant now, int recentDaysBack) {
        List<ListDto> safeLists = (lists == null) ? List.of() : lists;
        List<ItemDto> items = flattenItems(safeLists);

        int totalTasks = items.size();
        int doneTasks = 0;
        long completionSum = 0;
        int overdue = 0;
        int myOpen = 0;
        int addedRecently = 0;

        Map<String, Integer> mix = new LinkedHashMap<>();
        for (String s : KNOWN_STATUSES) {
            mix.put(s, 0);
        }

        LocalDate today = safeToday(now);
        Instant cutoff = safeCutoff(now, recentDaysBack);

        for (ItemDto it : items) {
            if (it == null) {
                continue;
            }

            String bucket = mixBucket(it.status());
            mix.merge(bucket, 1, Integer::sum);

            if (isDone(it)) {
                doneTasks++;
            }
            completionSum += completionPercentFor(it.status());

            if (isOverdue(it, today)) {
                overdue++;
            }
            if (isMyOpenTask(it, currentUserId)) {
                myOpen++;
            }
            if (isAddedSince(it, cutoff)) {
                addedRecently++;
            }
        }

        int completionPercent = (totalTasks == 0) ? 0 : Math.round((float) completionSum / totalTasks);

        return new DashboardStats(
                countNonNullLists(safeLists), totalTasks, doneTasks, completionPercent,
                overdue, myOpen, addedRecently, mix);
    }

    // -- per-stat pieces (each individually guarded) ----------------------------

    private static boolean isDone(ItemDto it) {
        try {
            return it.done();
        } catch (Exception e) {
            return false;
        }
    }

    /** Same mapping Completion/TaskStatus use: unknown/blank status -> 0. */
    private static int completionPercentFor(String status) {
        if (status == null) {
            return 0;
        }
        try {
            return TaskStatus.valueOf(status).getCompletionPercentage();
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    /** Unknown/null status buckets to NOT_STARTED, per the dashboard's status-mix rule. */
    private static String mixBucket(String status) {
        if (status != null) {
            for (String known : KNOWN_STATUSES) {
                if (known.equals(status)) {
                    return known;
                }
            }
        }
        return "NOT_STARTED";
    }

    /**
     * The exact overdue rule already used by the lists table's Overdue column
     * ({@link Helpers#toListEntry(ListDto)}): due date via
     * {@link Helpers#isoInstantToDate(String)}, not done, strictly before today.
     */
    private static boolean isOverdue(ItemDto it, LocalDate today) {
        if (it == null || today == null) {
            return false;
        }
        try {
            String due = Helpers.isoInstantToDate(it.dueAt());
            if (due == null || due.isBlank() || "DONE".equals(it.status())) {
                return false;
            }
            return LocalDate.parse(due).isBefore(today);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isMyOpenTask(ItemDto it, String currentUserId) {
        if (it == null || currentUserId == null || currentUserId.isBlank()) {
            return false;
        }
        try {
            return currentUserId.equals(it.assigneeId()) && !isDone(it);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isAddedSince(ItemDto it, Instant cutoff) {
        if (it == null || cutoff == null) {
            return false;
        }
        try {
            String created = it.createdAt();
            if (created == null || created.isBlank()) {
                return false;
            }
            return !Instant.parse(created).isBefore(cutoff);
        } catch (Exception e) {
            return false;
        }
    }

    private static LocalDate safeToday(Instant now) {
        try {
            return (now == null) ? null : now.atZone(ZoneOffset.UTC).toLocalDate();
        } catch (Exception e) {
            return null;
        }
    }

    private static Instant safeCutoff(Instant now, int daysBack) {
        try {
            return (now == null) ? null : now.minus(daysBack, ChronoUnit.DAYS);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Flattens every list's items into one collection, skipping null lists AND
     * null items within a list's items collection. Callers rely on {@code
     * .size()} of the returned list being a meaningful, already-filtered count
     * (it is the divisor behind {@code completionPercent} and the value behind
     * {@code totalTasks}), so nulls must never survive into it: a null slipping
     * through here would inflate both of those past what the per-item loop
     * actually summed.
     */
    private static List<ItemDto> flattenItems(List<ListDto> lists) {
        List<ItemDto> out = new ArrayList<>();
        for (ListDto l : lists) {
            if (l == null || l.items() == null) {
                continue;
            }
            for (ItemDto it : l.items()) {
                if (it != null) {
                    out.add(it);
                }
            }
        }
        return out;
    }

    /** Count of non-null lists; a null entry in the lists array must not inflate totalLists. */
    private static int countNonNullLists(List<ListDto> lists) {
        int count = 0;
        for (ListDto l : lists) {
            if (l != null) {
                count++;
            }
        }
        return count;
    }
}
