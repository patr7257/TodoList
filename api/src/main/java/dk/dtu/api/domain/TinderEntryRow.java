package dk.dtu.api.domain;

import java.time.Instant;

/**
 * One row of {@code tinder_entries} (issue #56): a single card in a deck.
 *
 * <p>{@code metadata} is carried as the RAW jsonb text exactly as Postgres
 * returned it, never as a parsed map. Two reasons, both deliberate. The
 * metadata schema is per deck and set by the dataset (issue #57): a destination
 * card carries a price band and a season, a grocery card carries a shop
 * section, and the API is not supposed to know or care about either. And the
 * refill endpoint (#59) accepts whatever object a Claude session generated, so
 * parsing it into a typed shape here would mean rejecting valid data the moment
 * a dataset grows a field. The view layer re-parses it once, on the way out, so
 * clients see a real JSON object rather than a string.
 */
public record TinderEntryRow(
        String id,
        String deckId,
        String text,
        String metadata,
        String source,
        boolean active,
        Instant createdAt) {
}
