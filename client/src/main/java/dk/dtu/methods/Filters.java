package dk.dtu.methods;

import java.util.function.Predicate;

/**
 * Pure "only mine" filter predicates for the lists and tasks views. Deliberately
 * JavaFX-free (no scene-graph imports) so it can be unit tested without a FX
 * toolkit.
 *
 * <p>Both predicates compare a real user id (the list's {@code ownerId} or the
 * task's {@code assigneeId}) against the signed-in user's id -- never a display
 * name. They fail OPEN (match everything) when {@code currentUserId} is
 * null/blank: at app start, before {@code GET /state} has landed, the
 * signed-in user id is not known yet, and hiding every row in that window would
 * read as "my data is gone" rather than "not filtered yet".
 */
public final class Filters {

    private Filters() {
    }

    /**
     * True when {@code entityUserId} (a list's ownerId or a task's assigneeId)
     * belongs to {@code currentUserId}. Fails open (returns true) when
     * {@code currentUserId} is null/blank.
     */
    public static boolean matchesOnlyMine(String entityUserId, String currentUserId) {
        if (currentUserId == null || currentUserId.isBlank()) {
            return true;
        }
        return currentUserId.equals(entityUserId);
    }

    /** "Only my lists" predicate, bound to a given signed-in user id. */
    public static Predicate<Helpers.ListEntry> onlyMyLists(String currentUserId) {
        return e -> e == null || matchesOnlyMine(e.ownerId, currentUserId);
    }

    /** "Only my tasks" predicate, bound to a given signed-in user id. */
    public static Predicate<Helpers.TaskEntry> onlyMyTasks(String currentUserId) {
        return e -> e == null || matchesOnlyMine(e.assigneeId, currentUserId);
    }
}
