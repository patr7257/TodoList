package dk.dtu.api.domain;

import java.time.Instant;

/** A row of the users table. */
public record UserRow(
        String id,
        String email,
        String name,
        String pwHash,
        Instant createdAt) {
}
