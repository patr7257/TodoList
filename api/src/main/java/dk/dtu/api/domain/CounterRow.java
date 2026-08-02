package dk.dtu.api.domain;

import java.time.Instant;

/**
 * One row of the shared, manually maintained {@code fun_counters} table (Total
 * Flights, Total Ships, Tour de Brede walks, ...). Desktop-owned; not part of
 * the website's Drizzle schema.
 */
public record CounterRow(
        String id,
        String label,
        String description,
        int value,
        String icon,
        int sort,
        String createdBy,
        Instant createdAt,
        Instant updatedAt) {
}
