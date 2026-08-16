package dk.dtu.api.domain;

import java.time.Instant;

/**
 * A row of the users table, minus the credential column.
 *
 * <p>{@code users.pw_hash} is deliberately NOT mapped here. It was, while
 * password login existed, and issue #61 removed both the login path and the
 * only reader. Leaving the field behind would keep loading a stored credential
 * into memory on every user lookup for nobody, and would leave it sitting one
 * careless {@code m.put} away from a response body: the API already has a whole
 * convention (see {@code ShareViews}) about fields that must never reach a
 * payload, and the cheapest way to honour it for this one is to not have it.
 *
 * <p>The column itself stays in Postgres, nullable and unread. Applied
 * migrations are immutable, and dropping it would buy nothing.
 */
public record UserRow(
        String id,
        String email,
        String name,
        Instant createdAt) {
}
