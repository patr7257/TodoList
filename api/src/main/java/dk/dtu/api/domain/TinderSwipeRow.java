package dk.dtu.api.domain;

import java.time.Instant;

/**
 * One row of {@code tinder_swipes} (issue #56): the LATEST swipe a user made on
 * a card, not an entry in a swipe log.
 *
 * <p>That distinction is load-bearing and is enforced by the unique index on
 * {@code (user_id, entry_id)} plus the upsert in
 * {@link TinderService#recordSwipe}. The recycling grocery deck offers the same
 * card every week, so an append-only table would grow without bound and a plain
 * insert would throw on the second week. See the long comment in
 * {@code V8__tinder.sql} for why the constraint cannot instead be made partial
 * on the deck's mode.
 */
public record TinderSwipeRow(
        String id,
        String userId,
        String entryId,
        String direction,
        Instant createdAt) {

    /** "yes please", which is what creates the todo item. */
    public static final String DIRECTION_RIGHT = "right";

    /**
     * "no thanks". On an idea deck that retires the card for this user; on the
     * grocery deck it means "not this run" and nothing more, with no cooldown,
     * because the same milk question every week is the point of that deck.
     */
    public static final String DIRECTION_LEFT = "left";

    /** True for exactly the two accepted wire values, which are lower case. */
    public static boolean isDirection(String value) {
        return DIRECTION_RIGHT.equals(value) || DIRECTION_LEFT.equals(value);
    }
}
