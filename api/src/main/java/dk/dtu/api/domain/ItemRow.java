package dk.dtu.api.domain;

import java.time.Instant;

/**
 * A row of the items table. All fields except {@code year} mirror the website
 * schema; {@code year} is the nullable desktop-superset column added in V2.
 */
public record ItemRow(
        String id,
        String listId,
        String text,
        String description,
        boolean done,
        String status,
        Integer priority,
        Instant dueAt,
        String location,
        String assigneeId,
        int sort,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        Integer year) {
}
