package dk.dtu.api.domain;

import java.time.Instant;

/**
 * A row of the lists table. The first four fields mirror the website schema;
 * the rest are the nullable desktop-superset columns added in migration V2.
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
        String taskColumnsJson) {
}
