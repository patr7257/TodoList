package dk.dtu.api.domain;

import java.time.Instant;

/**
 * One row of {@code tinder_decks} (issue #56): a swipe deck, its target todo
 * list, and the rule that decides whether a swiped card ever comes back.
 *
 * <p>A deck is config, not code. The whole point of holding decks in a table is
 * that a fifth deck is one row plus one dataset file rather than a code change,
 * so nothing in the API may branch on a specific deck key. It branches on
 * {@link #recycles()} instead, which is the only behavioural difference between
 * "the grocery deck" and "an idea deck".
 *
 * <p>{@code targetListId} is nullable and stays nullable: the column is
 * {@code ON DELETE SET NULL}, so deleting the Indkøb list must not delete the
 * grocery deck. A deck with no target list still records swipes and simply
 * creates nothing, which is a deliberately boring failure mode.
 */
public record TinderDeckRow(
        String id,
        String key,
        String displayName,
        String targetListId,
        String recycleMode,
        String datasetKey,
        boolean active,
        Instant createdAt) {

    /** Idea decks: a card the user swiped, either way, never comes back for them. */
    public static final String MODE_DEPLETE = "deplete";

    /** The grocery deck: every card is offered again on the next run, forever. */
    public static final String MODE_RECYCLE = "recycle";

    /**
     * True for the grocery deck. Drives BOTH the card query (no swipe filter at
     * all) and the swipe write (an upsert instead of an insert, because the same
     * (user, entry) pair is swiped again every week). Those two are the entire
     * difference between the modes; there is no third place to keep in sync.
     */
    public boolean recycles() {
        return MODE_RECYCLE.equalsIgnoreCase(recycleMode);
    }
}
