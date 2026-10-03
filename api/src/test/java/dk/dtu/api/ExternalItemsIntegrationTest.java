package dk.dtu.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import dk.dtu.api.db.Migrations;
import dk.dtu.api.domain.IntegrationService;
import dk.dtu.api.domain.NewItem;
import dk.dtu.api.domain.TodoService;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * V11 (external references on items) and, from Task 3 on, the
 * IntegrationService that writes them, against a real embedded Postgres.
 *
 * <p>Every test uses its own list names, because the todo space is shared and
 * global: a test that asserted on every list would be coupled to every other
 * test in the file. They are independent on purpose (no @Order).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExternalItemsIntegrationTest {

    private EmbeddedPostgres pg;
    private DataSource ds;
    private Jdbi jdbi;
    private TodoService todo;
    private String aliceId;
    private IntegrationService integrations;

    @BeforeAll
    void startDatabase() throws IOException {
        pg = EmbeddedPostgres.builder().start();
        ds = pg.getPostgresDatabase();
        Migrations.migrate(ds);
        jdbi = Jdbi.create(ds);
        todo = new TodoService(jdbi);
        aliceId = insertUser("Alice", "alice-ext");
        integrations = new IntegrationService(jdbi);
    }

    @AfterAll
    void stopDatabase() throws IOException {
        if (pg != null) {
            pg.close();
        }
    }

    // -- helpers ---------------------------------------------------------------

    private String insertUser(String name, String emailLocalPart) {
        jdbi.useHandle(h -> h
                .createUpdate("INSERT INTO users (email, name, pw_hash) VALUES (:e, :n, NULL)")
                .bind("e", emailLocalPart + "@example.com")
                .bind("n", name)
                .execute());
        return jdbi.withHandle(h -> h
                .createQuery("SELECT id FROM users WHERE email = :e")
                .bind("e", emailLocalPart + "@example.com")
                .mapTo(String.class)
                .one());
    }

    private void insertExternal(String listId, String source, String externalId) {
        jdbi.useHandle(h -> h
                .createUpdate("INSERT INTO items (list_id, text, external_source, external_id) "
                        + "VALUES (CAST(:l AS uuid), 'x', :s, :e)")
                .bind("l", listId)
                .bind("s", source)
                .bind("e", externalId)
                .execute());
    }

    private record DbItem(String id, String text, String description, boolean done, String status) {
    }

    private DbItem dbItem(String listId, String source, String externalId) {
        return jdbi.withHandle(h -> h
                .createQuery("SELECT CAST(id AS text) AS id, text, description, done, CAST(status AS text) AS status "
                        + "FROM items WHERE list_id = CAST(:l AS uuid) AND external_source = :s AND external_id = :e")
                .bind("l", listId)
                .bind("s", source)
                .bind("e", externalId)
                .map((rs, ctx) -> new DbItem(rs.getString("id"), rs.getString("text"),
                        rs.getString("description"), rs.getBoolean("done"), rs.getString("status")))
                .one());
    }

    private int listsNamed(String name, String ownerId) {
        return jdbi.withHandle(h -> h
                .createQuery("SELECT COUNT(*) FROM lists WHERE name = :n AND owner_id = CAST(:o AS uuid)")
                .bind("n", name)
                .bind("o", ownerId)
                .mapTo(Integer.class)
                .one());
    }

    private static IntegrationService.SyncItem item(String id, String text, String description, boolean done) {
        return new IntegrationService.SyncItem(id, text, description, done);
    }

    // -- V11 -------------------------------------------------------------------

    @Test
    void v11AddsTwoNullableExternalColumnsToItems() {
        int count = jdbi.withHandle(h -> h
                .createQuery("SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'items' AND column_name IN ('external_source', 'external_id') "
                        + "AND is_nullable = 'YES' AND data_type = 'text'")
                .mapTo(Integer.class)
                .one());
        assertEquals(2, count);
    }

    @Test
    void partialUniqueIndexRejectsASecondRowWithTheSameExternalRef() {
        String listId = todo.insertList("v11-dup").id();
        insertExternal(listId, "bartender", "gin");
        assertThrows(UnableToExecuteStatementException.class,
                () -> insertExternal(listId, "bartender", "gin"));
    }

    @Test
    void sameExternalIdIsAllowedInAnotherListOrAnotherSource() {
        String a = todo.insertList("v11-a").id();
        String b = todo.insertList("v11-b").id();
        insertExternal(a, "bartender", "gin");
        assertDoesNotThrow(() -> insertExternal(b, "bartender", "gin"));
        assertDoesNotThrow(() -> insertExternal(a, "other", "gin"));
    }

    @Test
    void itemsWithoutExternalRefAreNotConstrained() {
        String listId = todo.insertList("v11-plain").id();
        NewItem plain = new NewItem(listId, "milk", null, "NOT_STARTED", null, null, null, null, aliceId);
        todo.insertItem(plain);
        assertDoesNotThrow(() -> todo.insertItem(plain));
    }

    @Test
    void v11ScriptIsIdempotentWhenRunAgain() throws IOException {
        String sql;
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V11__item_external_ref.sql")) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertDoesNotThrow(() -> jdbi.useHandle(h -> h.createScript(sql).execute()));
        assertDoesNotThrow(() -> Migrations.migrate(ds));
    }

    // -- IntegrationService ----------------------------------------------------

    @Test
    void firstSyncCreatesTheListOwnedByTheCallerAndItsItems() {
        IntegrationService.SyncResult r = integrations.sync(aliceId, "Drinks-create", "bartender",
                List.of(item("gin", "Gin", "running low", false), item("tonic", "Tonic", null, false)));
        assertNotNull(r.listId());
        assertEquals(2, r.created());
        assertEquals(0, r.updated());
        assertEquals(0, r.closed());
        assertEquals(1, listsNamed("Drinks-create", aliceId));
        String owner = jdbi.withHandle(h -> h
                .createQuery("SELECT owner FROM lists WHERE id = CAST(:id AS uuid)")
                .bind("id", r.listId()).mapTo(String.class).one());
        assertEquals("Alice", owner);
        DbItem gin = dbItem(r.listId(), "bartender", "gin");
        assertEquals("Gin", gin.text());
        assertEquals("running low", gin.description());
        assertFalse(gin.done());
        assertEquals("NOT_STARTED", gin.status());
    }

    @Test
    void anIdenticalSecondSyncChangesNothingAndReusesTheList() {
        List<IntegrationService.SyncItem> batch = List.of(item("gin", "Gin", "running low", false));
        IntegrationService.SyncResult first = integrations.sync(aliceId, "Drinks-same", "bartender", batch);
        IntegrationService.SyncResult second = integrations.sync(aliceId, "Drinks-same", "bartender", batch);
        assertEquals(first.listId(), second.listId());
        assertEquals(0, second.created());
        assertEquals(0, second.updated());
        assertEquals(0, second.closed());
        assertEquals(1, listsNamed("Drinks-same", aliceId));
    }

    @Test
    void changedTextOrDescriptionCountsAsUpdated() {
        IntegrationService.SyncResult first = integrations.sync(aliceId, "Drinks-text", "bartender",
                List.of(item("gin", "Gin", "running low", false)));
        IntegrationService.SyncResult r = integrations.sync(aliceId, "Drinks-text", "bartender",
                List.of(item("gin", "Gin", "for Negroni", false)));
        assertEquals(1, r.updated());
        assertEquals("for Negroni", dbItem(first.listId(), "bartender", "gin").description());
        IntegrationService.SyncResult cleared = integrations.sync(aliceId, "Drinks-text", "bartender",
                List.of(item("gin", "London Dry Gin", null, false)));
        assertEquals(1, cleared.updated());
        DbItem gin = dbItem(first.listId(), "bartender", "gin");
        assertEquals("London Dry Gin", gin.text());
        assertNull(gin.description());
    }

    @Test
    void doneTrueClosesOnceAndDoneFalseReopens() {
        String listId = integrations.sync(aliceId, "Drinks-done", "bartender",
                List.of(item("gin", "Gin", null, false))).listId();
        IntegrationService.SyncResult closed = integrations.sync(aliceId, "Drinks-done", "bartender",
                List.of(item("gin", "Gin", null, true)));
        assertEquals(1, closed.closed());
        assertEquals(0, closed.updated());
        DbItem gin = dbItem(listId, "bartender", "gin");
        assertTrue(gin.done());
        assertEquals("DONE", gin.status());

        IntegrationService.SyncResult again = integrations.sync(aliceId, "Drinks-done", "bartender",
                List.of(item("gin", "Gin", null, true)));
        assertEquals(0, again.closed());

        IntegrationService.SyncResult reopened = integrations.sync(aliceId, "Drinks-done", "bartender",
                List.of(item("gin", "Gin", null, false)));
        assertEquals(1, reopened.updated());
        assertEquals(0, reopened.closed());
        gin = dbItem(listId, "bartender", "gin");
        assertFalse(gin.done());
        assertEquals("NOT_STARTED", gin.status());
    }

    @Test
    void doneFalseReopensAnItemTickedInTodoList() {
        // Review Focus 4: the contract reopens. The bartender client must GET and
        // reverse-sync BEFORE it PUTs, or a tick made in TodoList is undone here.
        String listId = integrations.sync(aliceId, "Drinks-ticked", "bartender",
                List.of(item("lime", "Lime", null, false))).listId();
        String id = dbItem(listId, "bartender", "lime").id();
        jdbi.useHandle(h -> h.createUpdate(
                        "UPDATE items SET done = true, status = 'DONE' WHERE id = CAST(:id AS uuid)")
                .bind("id", id).execute());
        IntegrationService.SyncResult r = integrations.sync(aliceId, "Drinks-ticked", "bartender",
                List.of(item("lime", "Lime", null, false)));
        assertEquals(1, r.updated());
        assertFalse(dbItem(listId, "bartender", "lime").done());
    }

    @Test
    void createdDoneItemCountsOnlyAsCreated() {
        IntegrationService.SyncResult r = integrations.sync(aliceId, "Drinks-created-done", "bartender",
                List.of(item("rum", "Rum", null, true)));
        assertEquals(1, r.created());
        assertEquals(0, r.closed());
        DbItem rum = dbItem(r.listId(), "bartender", "rum");
        assertTrue(rum.done());
        assertEquals("DONE", rum.status());
    }

    @Test
    void itemsNotMentionedAreUntouched() {
        String listId = integrations.sync(aliceId, "Drinks-untouched", "bartender",
                List.of(item("a", "A", null, false), item("b", "B", "keep", false))).listId();
        integrations.sync(aliceId, "Drinks-untouched", "bartender", List.of(item("a", "A", null, true)));
        DbItem b = dbItem(listId, "bartender", "b");
        assertFalse(b.done());
        assertEquals("B", b.text());
        assertEquals("keep", b.description());
    }

    @Test
    void otherSourcesAreIsolatedInTheSameList() {
        String listId = integrations.sync(aliceId, "Drinks-sources", "bartender",
                List.of(item("gin", "Gin", null, false))).listId();
        IntegrationService.SyncResult other = integrations.sync(aliceId, "Drinks-sources", "other",
                List.of(item("gin", "Gin", null, false)));
        assertEquals(listId, other.listId());
        assertEquals(1, other.created());
        assertEquals(1, integrations.read(aliceId, "Drinks-sources", "bartender").items().size());
    }

    @Test
    void readReturnsExternalItemsOrderedByExternalId() {
        integrations.sync(aliceId, "Drinks-read", "bartender",
                List.of(item("tonic", "Tonic", null, true), item("gin", "Gin", null, false)));
        IntegrationService.ListItems read = integrations.read(aliceId, "Drinks-read", "bartender");
        assertNotNull(read.listId());
        assertEquals(List.of(
                new IntegrationService.ExternalItem("gin", "Gin", false),
                new IntegrationService.ExternalItem("tonic", "Tonic", true)), read.items());
    }

    @Test
    void readOfAnUnknownListHasNullListIdAndNoItems() {
        IntegrationService.ListItems read = integrations.read(aliceId, "Drinks-never-made", "bartender");
        assertNull(read.listId());
        assertTrue(read.items().isEmpty());
    }

    @Test
    void listLookupIsScopedToTheIntegrationUser() {
        String bobId = insertUser("Bob", "bob-ext");
        String bobsList = todo.insertList("Drinks-scope", "Bob", bobId).id();
        IntegrationService.SyncResult r = integrations.sync(aliceId, "Drinks-scope", "bartender",
                List.of(item("gin", "Gin", null, false)));
        assertNotEquals(bobsList, r.listId());
        assertEquals(1, listsNamed("Drinks-scope", aliceId));
    }

    @Test
    void deletedItemIsRecreatedOnTheNextSync() {
        // Review Focus 5.
        String listId = integrations.sync(aliceId, "Drinks-deleted", "bartender",
                List.of(item("gin", "Gin", null, false))).listId();
        assertTrue(todo.deleteItem(dbItem(listId, "bartender", "gin").id()));
        IntegrationService.SyncResult r = integrations.sync(aliceId, "Drinks-deleted", "bartender",
                List.of(item("gin", "Gin", null, false)));
        assertEquals(1, r.created());
    }

    @Test
    void concurrentFirstSyncsCreateExactlyOneListAndOneItem() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<IntegrationService.SyncResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return integrations.sync(aliceId, "Drinks-race", "bartender",
                        List.of(item("gin", "Gin", null, false)));
            }));
        }
        start.countDown();
        int created = 0;
        String listId = null;
        for (Future<IntegrationService.SyncResult> f : futures) {
            IntegrationService.SyncResult r = f.get(30, TimeUnit.SECONDS);
            created += r.created();
            if (listId == null) {
                listId = r.listId();
            }
            assertEquals(listId, r.listId());
        }
        pool.shutdown();
        assertEquals(1, created);
        assertEquals(1, listsNamed("Drinks-race", aliceId));
    }
}
