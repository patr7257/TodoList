package dk.dtu.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dk.dtu.api.auth.AuthService;
import dk.dtu.api.auth.Scrypt;
import dk.dtu.api.auth.Token;
import dk.dtu.api.db.Migrations;
import dk.dtu.api.domain.ColumnValue;
import dk.dtu.api.domain.Completion;
import dk.dtu.api.domain.ItemRow;
import dk.dtu.api.domain.ListRow;
import dk.dtu.api.domain.NewItem;
import dk.dtu.api.domain.TodoService;
import dk.dtu.api.domain.UserRow;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Types;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * End-to-end tests against a real (embedded) Postgres: Flyway migrations create
 * the schema from V1 and add the superset columns in V2, a seeded user logs in
 * through the scrypt path, and a list + item round-trips through the service.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TodoApiIntegrationTest {

    private static final String USER_EMAIL = "seed@example.com";
    private static final String USER_PASSWORD = "s3cret-password";

    private EmbeddedPostgres pg;
    private TodoService todo;
    private AuthService auth;
    private String seedUserId;

    @BeforeAll
    void startDatabase() throws IOException {
        pg = EmbeddedPostgres.builder().start();
        DataSource ds = pg.getPostgresDatabase();
        Migrations.migrate(ds);

        Jdbi jdbi = Jdbi.create(ds);
        todo = new TodoService(jdbi);
        auth = new AuthService(todo, new Token("integration-secret"));

        String stored = Scrypt.hash(USER_PASSWORD);
        jdbi.useHandle(h -> h
                .createUpdate("INSERT INTO users (email, name, pw_hash) VALUES (:e, :n, :p)")
                .bind("e", USER_EMAIL)
                .bind("n", "Seed User")
                .bind("p", stored)
                .execute());
        seedUserId = todo.findUserByEmail(USER_EMAIL).orElseThrow().id();
    }

    @AfterAll
    void stopDatabase() throws IOException {
        if (pg != null) {
            pg.close();
        }
    }

    @Test
    void migrationsAddedTheDesktopSupersetColumns() {
        Jdbi jdbi = Jdbi.create(pg.getPostgresDatabase());
        List<String> listCols = jdbi.withHandle(h -> h
                .createQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'lists'")
                .mapTo(String.class)
                .list());
        assertTrue(listCols.containsAll(List.of(
                "owner", "priority", "year", "location", "description", "task_columns_json")),
                "V2 should add the superset columns to lists, got " + listCols);

        List<String> itemCols = jdbi.withHandle(h -> h
                .createQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'items'")
                .mapTo(String.class)
                .list());
        assertTrue(itemCols.contains("year"), "V2 should add items.year, got " + itemCols);
    }

    @Test
    void loginSucceedsForSeededUserAndFailsOnBadCredentials() {
        Optional<AuthService.LoginResult> ok = auth.login(USER_EMAIL, USER_PASSWORD);
        assertTrue(ok.isPresent(), "seeded user should log in");
        assertEquals(USER_EMAIL, ok.get().user().email());
        assertNotNull(ok.get().token(), "a token should be issued");

        // Case-insensitive email, matching the website's normalization.
        assertTrue(auth.login("SEED@EXAMPLE.COM", USER_PASSWORD).isPresent());

        assertTrue(auth.login(USER_EMAIL, "wrong").isEmpty(), "wrong password -> no login");
        assertTrue(auth.login("nobody@example.com", USER_PASSWORD).isEmpty(), "unknown user -> no login");
    }

    @Test
    void listAndItemCreateReadRoundTrip() {
        ListRow list = todo.insertList("Roadmap");
        assertNotNull(list.id());
        assertEquals("Roadmap", list.name());
        assertEquals(0, list.sort());
        assertTrue(todo.listExists(list.id()));

        Instant due = Instant.parse("2026-08-01T09:00:00Z");
        ItemRow item = todo.insertItem(new NewItem(
                list.id(), "Ship v2", "the big one", "IN_PROGRESS",
                2, due, "Copenhagen", seedUserId, seedUserId));

        assertNotNull(item.id());
        assertEquals(list.id(), item.listId());
        assertEquals("Ship v2", item.text());
        assertEquals("the big one", item.description());
        assertEquals("IN_PROGRESS", item.status());
        assertFalse(item.done());
        assertEquals(2, item.priority());
        assertEquals(due, item.dueAt());
        assertEquals("Copenhagen", item.location());
        assertEquals(seedUserId, item.assigneeId());
        assertEquals(seedUserId, item.createdBy());

        // Read back through the ordered queries used by GET /state.
        List<ListRow> lists = todo.allListsOrdered();
        assertTrue(lists.stream().anyMatch(l -> l.id().equals(list.id())));
        List<ItemRow> items = todo.allItemsOrdered();
        assertTrue(items.stream().anyMatch(i -> i.id().equals(item.id())));

        // Update: status DONE derives done=true.
        Optional<ItemRow> updated = todo.updateItem(item.id(), List.of(
                new ColumnValue("status", "CAST(:status AS todo_status)", "DONE", Types.VARCHAR),
                new ColumnValue("done", ":done", true, Types.BOOLEAN)));
        assertTrue(updated.isPresent());
        assertEquals("DONE", updated.get().status());
        assertTrue(updated.get().done());

        // Delete list cascades to its items.
        assertTrue(todo.deleteList(list.id()));
        assertFalse(todo.listExists(list.id()));
        assertTrue(todo.allItemsOrdered().stream().noneMatch(i -> i.id().equals(item.id())),
                "items should cascade-delete with their list");
    }

    @Test
    void completionReflectsItemStatusesOnARealList() {
        ListRow list = todo.insertList("Completion check");
        todo.insertItem(new NewItem(list.id(), "a", null, "NOT_STARTED", null, null, null, null, seedUserId));
        todo.insertItem(new NewItem(list.id(), "b", null, "IN_PROGRESS", null, null, null, null, seedUserId));
        todo.insertItem(new NewItem(list.id(), "c", null, "DONE", null, null, null, null, seedUserId));

        List<ItemRow> items = todo.allItemsOrdered().stream()
                .filter(i -> i.listId().equals(list.id()))
                .toList();
        assertEquals(3, items.size());
        // 0 + 50 + 100 = 150 / 3 = 50
        assertEquals(50, Completion.forItems(items));

        todo.deleteList(list.id());
    }
}
