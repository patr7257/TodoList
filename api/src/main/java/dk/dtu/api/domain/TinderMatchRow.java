package dk.dtu.api.domain;

import java.time.Instant;

/**
 * One couple match: a card every user has swiped right on (issue #56).
 *
 * <p>There is no {@code tinder_matches} table and there must never be one. A
 * match is DERIVED, by {@link TinderService#matches()}, from the swipes it
 * describes, so it cannot drift out of sync with them: unswipe one side and the
 * match is simply not in the next answer. A stored flag would need every write
 * path that touches a swipe to remember to recompute it, and the one that
 * forgot would leave a permanent lie in the UI.
 *
 * <p>{@code matchedAt} is therefore not a stored timestamp either: it is the
 * latest of the contributing right swipes, that is, the moment the match came
 * into existence.
 */
public record TinderMatchRow(
        String entryId,
        String deckKey,
        String deckDisplayName,
        String text,
        String metadata,
        Instant matchedAt) {
}
