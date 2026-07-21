package dk.dtu.api.domain;

import java.time.Instant;

/**
 * Validated input for creating an item. Nullable fields are already resolved
 * (empty text collapsed to null) by the controller before reaching the service.
 */
public record NewItem(
        String listId,
        String text,
        String description,
        String status,
        Integer priority,
        Instant dueAt,
        String location,
        String assigneeId,
        String createdBy) {
}
