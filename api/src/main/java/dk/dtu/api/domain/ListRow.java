package dk.dtu.api.domain;

import java.time.Instant;

/**
 * A row of the lists table. The first four fields mirror the website schema;
 * the rest are the nullable desktop-superset columns added in migration V2,
 * plus {@code ownerId} (the real user reference added in V3, appended last).
 * {@code owner} stays the denormalized display-name text (kept in lockstep
 * with {@code ownerId} by the write path); the resolved owner name for a
 * given {@code ownerId} is looked up in the view layer, not stored here.
 */
public record ListRow(
        String id,
        String name,
        int sort,
        Instant createdAt,
        String owner,
        Integer priority,
        Integer year,
        String location,
        String description,
        String taskColumnsJson,
        String ownerId) {
}
