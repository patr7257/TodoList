package dk.dtu.api.domain;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Update;

/**
 * Lists kept in sync by an external system (the integration API). Its own
 * service, like TinderService, because TodoService mirrors the website's
 * queries and is the hottest file in the repo.
 *
 * <p>An item belongs to an integration when it carries
 * {@code (external_source, external_id)} (V11). The integration owns the text
 * and description of its items; a person owns whether they are done, and the
 * integration can read that back.
 *
 * <p>Concurrency. Two simultaneous first syncs for one list name must not
 * create two lists. A unique index cannot express "one list per owner and
 * name": {@code lists.name} is free text the website writes, duplicates may
 * already exist in production, and V11 must stay additive. So {@link #sync}
 * takes {@code pg_advisory_xact_lock} on a hash of (user, list name) as its
 * first statement. The second caller blocks until the first commits; under
 * READ COMMITTED its next statement takes a fresh snapshot and finds the list.
 * The lock is released by commit or rollback, so it can never leak, and a hash
 * collision only serialises two unrelated syncs, which is harmless. The same
 * lock is why item inserts never race on the V11 unique index.
 */
public final class IntegrationService {

    /** One incoming item. Text is trimmed and description normalised by the caller. */
    public record SyncItem(String externalId, String text, String description, boolean done) {
    }

    public record SyncResult(String listId, int created, int updated, int closed) {
    }

    public record ExternalItem(String externalId, String text, boolean done) {
    }

    /** {@code listId} is null when the caller has no list of that name. */
    public record ListItems(String listId, List<ExternalItem> items) {
    }

    private record Existing(String id, String text, String description, boolean done) {
    }

    private final Jdbi jdbi;

    public IntegrationService(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public SyncResult sync(String userId, String listName, String source, List<SyncItem> items) {
        return jdbi.inTransaction(h -> {
            lockList(h, userId, listName);
            String listId = findListId(h, userId, listName);
            if (listId == null) {
                listId = createList(h, userId, listName);
            }
            Map<String, Existing> existing = existingItems(h, listId, source);
            Timestamp now = Timestamp.from(Instant.now());
            int created = 0;
            int updated = 0;
            int closed = 0;
            for (SyncItem in : items) {
                Existing cur = existing.get(in.externalId());
                if (cur == null) {
                    insertItem(h, listId, source, in, userId);
                    created++;
                    continue;
                }
                boolean textChanged = !cur.text().equals(in.text())
                        || !Objects.equals(cur.description(), in.description());
                if (textChanged) {
                    updateText(h, cur.id(), in, now);
                }
                if (in.done() && !cur.done()) {
                    setDone(h, cur.id(), true, now);
                    closed++;
                }
                boolean reopened = !in.done() && cur.done();
                if (reopened) {
                    setDone(h, cur.id(), false, now);
                }
                if (textChanged || reopened) {
                    updated++;
                }
            }
            return new SyncResult(listId, created, updated, closed);
        });
    }

    public ListItems read(String userId, String listName, String source) {
        return jdbi.withHandle(h -> {
            String listId = findListId(h, userId, listName);
            if (listId == null) {
                return new ListItems(null, List.of());
            }
            List<ExternalItem> items = h
                    .createQuery("SELECT external_id, text, done FROM items "
                            + "WHERE list_id = CAST(:listId AS uuid) AND external_source = :source "
                            + "ORDER BY external_id ASC")
                    .bind("listId", listId)
                    .bind("source", source)
                    .map((rs, ctx) -> new ExternalItem(
                            rs.getString("external_id"), rs.getString("text"), rs.getBoolean("done")))
                    .list();
            return new ListItems(listId, items);
        });
    }

    private static void lockList(Handle h, String userId, String listName) {
        h.createQuery("SELECT true FROM (SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))) AS locked")
                .bind("key", "todo-integration-list:" + userId + ":" + listName)
                .mapTo(Boolean.class)
                .one();
    }

    private static String findListId(Handle h, String userId, String listName) {
        return h.createQuery("SELECT CAST(id AS text) FROM lists "
                        + "WHERE name = :name AND owner_id = CAST(:uid AS uuid) "
                        + "ORDER BY created_at ASC, id ASC LIMIT 1")
                .bind("name", listName)
                .bind("uid", userId)
                .mapTo(String.class)
                .findOne()
                .orElse(null);
    }

    private static String createList(Handle h, String userId, String listName) {
        // owner is the denormalised display name CLAUDE.md requires kept in
        // sync with owner_id, so it is copied from the user row in the same
        // statement rather than looked up separately.
        return h.createQuery("INSERT INTO lists (name, owner, owner_id) "
                        + "SELECT :name, u.name, u.id FROM users u WHERE u.id = CAST(:uid AS uuid) "
                        + "RETURNING CAST(id AS text)")
                .bind("name", listName)
                .bind("uid", userId)
                .mapTo(String.class)
                .findOne()
                .orElseThrow(() -> new IllegalStateException("integration user does not exist"));
    }

    private static Map<String, Existing> existingItems(Handle h, String listId, String source) {
        Map<String, Existing> out = new HashMap<>();
        h.createQuery("SELECT CAST(id AS text) AS id, external_id, text, description, done FROM items "
                        + "WHERE list_id = CAST(:listId AS uuid) AND external_source = :source")
                .bind("listId", listId)
                .bind("source", source)
                .map((rs, ctx) -> Map.entry(rs.getString("external_id"), new Existing(
                        rs.getString("id"), rs.getString("text"),
                        rs.getString("description"), rs.getBoolean("done"))))
                .forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    private static void insertItem(Handle h, String listId, String source, SyncItem in, String userId) {
        String status = in.done() ? "DONE" : "NOT_STARTED";
        Update u = h.createUpdate("INSERT INTO items "
                + "(list_id, text, description, status, done, created_by, external_source, external_id) "
                + "VALUES (CAST(:listId AS uuid), :text, :description, CAST(:status AS todo_status), :done, "
                + "CAST(:createdBy AS uuid), :source, :externalId)");
        u.bind("listId", listId);
        u.bind("text", in.text());
        bindNullableText(u, "description", in.description());
        u.bind("status", status);
        u.bind("done", in.done());
        u.bind("createdBy", userId);
        u.bind("source", source);
        u.bind("externalId", in.externalId());
        u.execute();
    }

    private static void updateText(Handle h, String itemId, SyncItem in, Timestamp now) {
        Update u = h.createUpdate("UPDATE items SET text = :text, description = :description, "
                + "updated_at = :now WHERE id = CAST(:id AS uuid)");
        u.bind("text", in.text());
        bindNullableText(u, "description", in.description());
        u.bind("now", now);
        u.bind("id", itemId);
        u.execute();
    }

    private static void setDone(Handle h, String itemId, boolean done, Timestamp now) {
        h.createUpdate("UPDATE items SET done = :done, status = CAST(:status AS todo_status), "
                        + "updated_at = :now WHERE id = CAST(:id AS uuid)")
                .bind("done", done)
                .bind("status", done ? "DONE" : "NOT_STARTED")
                .bind("now", now)
                .bind("id", itemId)
                .execute();
    }

    private static void bindNullableText(Update u, String name, String value) {
        if (value == null) {
            u.bindNull(name, Types.VARCHAR);
        } else {
            u.bind(name, value);
        }
    }
}
