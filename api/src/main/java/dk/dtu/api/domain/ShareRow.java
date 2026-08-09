package dk.dtu.api.domain;

import java.time.Instant;

/**
 * One row of the {@code list_shares} table (issue #52): a public, revocable
 * read-only link to a single list. Mirrors the V6 columns one to one.
 *
 * <p>{@code token} is a bearer secret. It is safe to carry here because this
 * record only ever reaches the AUTHENTICATED management views; the public
 * payload built by {@code ShareViews.publicList} never includes it (the holder
 * already has it, and echoing it back would put it in more logs for nothing).
 */
public record ShareRow(
        String id,
        String listId,
        String token,
        String label,
        String createdBy,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        Instant lastViewedAt,
        int viewCount) {
}
