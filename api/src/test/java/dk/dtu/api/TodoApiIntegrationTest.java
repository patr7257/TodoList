package dk.dtu.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
import dk.dtu.api.web.ApiServer;
import dk.dtu.api.web.Backend;
import dk.dtu.api.web.RateLimiter;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.Javalin;
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

    // HTTP-level harness: a real Javalin app (dk.dtu.api.web.ApiServer, wired
    // exactly like ApiMain does) bound to a random local port, so the ownerId
    // validation/400-vs-500 behavior in ListsController is exercised the same
    // way the desktop client hits it, not just through TodoService directly.
    private Javalin app;
    private String baseUrl;
    private String bearerToken;
    private final HttpClient http = HttpClient.newHttpClient();

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

        Backend backend = new Backend(
                ApiConfig.of(0, null, "integration-secret", 0, 600),
                todo, auth, auth.token(), new RateLimiter(0, 600));
        app = ApiServer.create(backend);
        app.start(0);
        baseUrl = "http://127.0.0.1:" + app.port();
        // Minted directly rather than obtained by logging in with a password, the
        // way CountersIntegrationTest already does it. Password login is on its
        // way out (issue #51 moves sign-in to passkeys plus magic link), and this
        // suite should not stop working the day it is finally deleted. The
        // password path still has its own dedicated test below.
        bearerToken = auth.token().issue(seedUserId);
    }

    @AfterAll
    void stopDatabase() throws IOException {
        if (app != null) {
            app.stop();
        }
        if (pg != null) {
            pg.close();
        }
    }

    // -- HTTP test helpers -------------------------------------------------

    private HttpResponse<String> httpPatch(String path, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/todo" + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + bearerToken)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> httpPost(String path, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/todo" + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + bearerToken)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    /** Inserts a plain test user directly (bypasses the API's own signup, which does not exist). */
    private String insertTestUser(Jdbi jdbi, String name, String emailLocalPart) {
        String hash = Scrypt.hash("irrelevant-password");
        jdbi.useHandle(h -> h
                .createUpdate("INSERT INTO users (email, name, pw_hash) VALUES (:e, :n, :p)")
                .bind("e", emailLocalPart + "@example.com")
                .bind("n", name)
                .bind("p", hash)
                .execute());
        return jdbi.withHandle(h -> h
                .createQuery("SELECT id FROM users WHERE email = :e")
                .bind("e", emailLocalPart + "@example.com")
                .mapTo(String.class)
                .one());
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
    void v7AddsPasskeyCredentialsAndMakesPasswordHashOptional() {
        Jdbi jdbi = Jdbi.create(pg.getPostgresDatabase());

        List<String> credCols = jdbi.withHandle(h -> h
                .createQuery("SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = 'todo_credentials'")
                .mapTo(String.class)
                .list());
        assertTrue(credCols.containsAll(List.of(
                "id", "user_id", "public_key", "counter", "transports", "device_name",
                "created_at", "last_used_at")),
                "V7 should create todo_credentials with the website's expected columns, got " + credCols);

        // The website's WebAuthn code looks credentials up by the authenticator's
        // own credential id, so that column has to be the primary key rather than
        // a generated uuid.
        String pkColumn = jdbi.withHandle(h -> h
                .createQuery("""
                        SELECT kcu.column_name
                        FROM information_schema.table_constraints tc
                        JOIN information_schema.key_column_usage kcu
                          ON kcu.constraint_name = tc.constraint_name
                        WHERE tc.table_name = 'todo_credentials'
                          AND tc.constraint_type = 'PRIMARY KEY'
                        """)
                .mapTo(String.class)
                .one());
        assertEquals("id", pkColumn, "todo_credentials primary key should be the credential id");

        // The kill switch for password login depends on this being nullable: a
        // passkey-only account has no password, and UPDATE users SET pw_hash =
        // NULL must be a legal way to turn password sign-in off.
        String nullable = jdbi.withHandle(h -> h
                .createQuery("SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_name = 'users' AND column_name = 'pw_hash'")
                .mapTo(String.class)
                .one());
        assertEquals("YES", nullable, "V7 should relax users.pw_hash to nullable");
    }

    @Test
    void passwordLoginForAUserWithNullPasswordHashIsRejectedNotAnError() {
        Jdbi jdbi = Jdbi.create(pg.getPostgresDatabase());
        String email = "passkey-only@example.com";
        jdbi.useHandle(h -> h
                .createUpdate("INSERT INTO users (email, name, pw_hash) VALUES (:e, :n, NULL)")
                .bind("e", email)
                .bind("n", "Passkey Only")
                .execute());

        // This is what nulling pw_hash has to do: refuse the login cleanly. If it
        // ever threw instead, the kill switch would take the login route down
        // with a 500 rather than turning password auth off, and #51's rollback
        // plan would be worthless.
        assertTrue(auth.login(email, "anything").isEmpty(),
                "a user with no password hash must fail to log in rather than error");
        assertTrue(auth.login(email, "").isEmpty(),
                "an empty password against a null hash must also just fail");
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
    void listSupersetFieldsWriteAndReadBack() {
        // Create carries an optional owner.
        ListRow created = todo.insertList("Trip", "Alice");
        assertEquals("Alice", created.owner());
        assertNull(created.priority());
        assertNull(created.year());
        assertNull(created.location());
        assertNull(created.description());

        // Update persists every desktop-superset field.
        Optional<ListRow> updated = todo.updateList(created.id(), List.of(
                new ColumnValue("owner", ":owner", "Bob", Types.VARCHAR),
                new ColumnValue("priority", ":priority", 3, Types.INTEGER),
                new ColumnValue("year", ":year", 2027, Types.INTEGER),
                new ColumnValue("location", ":location", "Aarhus", Types.VARCHAR),
                new ColumnValue("description", ":description", "sommerferie", Types.VARCHAR)));
        assertTrue(updated.isPresent());
        assertEquals("Bob", updated.get().owner());
        assertEquals(3, updated.get().priority());
        assertEquals(2027, updated.get().year());
        assertEquals("Aarhus", updated.get().location());
        assertEquals("sommerferie", updated.get().description());

        // Nulls clear the fields (desktop clears an owner/location/etc.).
        Optional<ListRow> cleared = todo.updateList(created.id(), List.of(
                new ColumnValue("owner", ":owner", null, Types.VARCHAR),
                new ColumnValue("priority", ":priority", null, Types.INTEGER),
                new ColumnValue("year", ":year", null, Types.INTEGER),
                new ColumnValue("location", ":location", null, Types.VARCHAR),
                new ColumnValue("description", ":description", null, Types.VARCHAR)));
        assertTrue(cleared.isPresent());
        assertNull(cleared.get().owner());
        assertNull(cleared.get().priority());
        assertNull(cleared.get().year());
        assertNull(cleared.get().location());
        assertNull(cleared.get().description());

        // Read back through the ordered query used by GET /state after re-setting.
        todo.updateList(created.id(), List.of(
                new ColumnValue("year", ":year", 2030, Types.INTEGER)));
        ListRow reread = todo.allListsOrdered().stream()
                .filter(l -> l.id().equals(created.id())).findFirst().orElseThrow();
        assertEquals(2030, reread.year());

        todo.deleteList(created.id());
    }

    @Test
    void itemYearWritesAndReadsBack() {
        ListRow list = todo.insertList("Years");
        ItemRow item = todo.insertItem(new NewItem(
                list.id(), "task", null, "NOT_STARTED", null, null, null, null, seedUserId));
        assertNull(item.year(), "year defaults to null on create");

        Optional<ItemRow> updated = todo.updateItem(item.id(), List.of(
                new ColumnValue("year", ":year", 2029, Types.INTEGER)));
        assertTrue(updated.isPresent());
        assertEquals(2029, updated.get().year());

        Optional<ItemRow> cleared = todo.updateItem(item.id(), List.of(
                new ColumnValue("year", ":year", null, Types.INTEGER)));
        assertTrue(cleared.isPresent());
        assertNull(cleared.get().year());

        todo.deleteList(list.id());
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

    // -- V3: lists.owner_id column, FK, index -----------------------------

    @Test
    void v3AddsOwnerIdAsAForeignKeyToUsersWithAnIndex() {
        Jdbi jdbi = Jdbi.create(pg.getPostgresDatabase());

        List<String> listCols = jdbi.withHandle(h -> h
                .createQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'lists'")
                .mapTo(String.class)
                .list());
        assertTrue(listCols.contains("owner_id"), "V3 should add lists.owner_id, got " + listCols);

        List<String> fkTargets = jdbi.withHandle(h -> h
                .createQuery("""
                        SELECT ccu.table_name
                        FROM information_schema.table_constraints tc
                        JOIN information_schema.key_column_usage kcu
                          ON tc.constraint_name = kcu.constraint_name
                        JOIN information_schema.constraint_column_usage ccu
                          ON tc.constraint_name = ccu.constraint_name
                        WHERE tc.table_name = 'lists'
                          AND kcu.column_name = 'owner_id'
                          AND tc.constraint_type = 'FOREIGN KEY'
                        """)
                .mapTo(String.class)
                .list());
        assertEquals(List.of("users"), fkTargets, "lists.owner_id must be a FK to users(id)");

        List<String> indexNames = jdbi.withHandle(h -> h
                .createQuery("SELECT indexname FROM pg_indexes WHERE tablename = 'lists'")
                .mapTo(String.class)
                .list());
        assertTrue(indexNames.contains("lists_owner_id_idx"),
                "V3 should add an index on lists.owner_id, got " + indexNames);
    }

    // -- V4: backfill (executed verbatim off the classpath against ad-hoc test data) --

    @Test
    void v4BackfillMatchesExactCaseAndWhitespaceInsensitiveAndLeavesAmbiguousOrUnmatchedNull() throws Exception {
        Jdbi jdbi = Jdbi.create(pg.getPostgresDatabase());
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        String uniqueName = "Backfill-Unique-" + suffix;
        String dupName = "Backfill-Dup-" + suffix;
        String noMatchOwnerText = "Backfill-NoMatch-" + suffix;

        String uniqueUserId = insertTestUser(jdbi, uniqueName, "backfill-unique-" + suffix);
        insertTestUser(jdbi, dupName, "backfill-dup1-" + suffix);
        insertTestUser(jdbi, dupName, "backfill-dup2-" + suffix); // same name -> ambiguous

        String exactId = insertRawList(jdbi, "L-exact-" + suffix, uniqueName);
        String paddedId = insertRawList(jdbi, "L-padded-" + suffix, "  " + uniqueName.toUpperCase() + "  ");
        String ambiguousId = insertRawList(jdbi, "L-ambiguous-" + suffix, dupName);
        String noMatchId = insertRawList(jdbi, "L-nomatch-" + suffix, noMatchOwnerText);
        String nullOwnerId = insertRawList(jdbi, "L-nullowner-" + suffix, null);
        String blankOwnerId = insertRawList(jdbi, "L-blankowner-" + suffix, "   ");

        runBackfillMigration(jdbi);

        assertEquals(uniqueUserId, ownerIdOf(jdbi, exactId), "exact (case-sensitive) name match should backfill");
        assertEquals(uniqueUserId, ownerIdOf(jdbi, paddedId),
                "case-insensitive, whitespace-padded name match should backfill");
        assertNull(ownerIdOf(jdbi, ambiguousId), "a name matching two users must stay NULL");
        assertNull(ownerIdOf(jdbi, noMatchId), "a name matching no user must stay NULL");
        assertNull(ownerIdOf(jdbi, nullOwnerId), "a NULL owner must stay NULL");
        assertNull(ownerIdOf(jdbi, blankOwnerId), "a blank owner must stay NULL");

        // Re-running the migration is a no-op: nothing changes, no error.
        runBackfillMigration(jdbi);
        assertEquals(uniqueUserId, ownerIdOf(jdbi, exactId));
        assertEquals(uniqueUserId, ownerIdOf(jdbi, paddedId));
        assertNull(ownerIdOf(jdbi, ambiguousId));
        assertNull(ownerIdOf(jdbi, noMatchId));
        assertNull(ownerIdOf(jdbi, nullOwnerId));
        assertNull(ownerIdOf(jdbi, blankOwnerId));

        todo.deleteList(exactId);
        todo.deleteList(paddedId);
        todo.deleteList(ambiguousId);
        todo.deleteList(noMatchId);
        todo.deleteList(nullOwnerId);
        todo.deleteList(blankOwnerId);
    }

    /** Inserts a list row directly (bypassing TodoService, since owner_id must start NULL). */
    private String insertRawList(Jdbi jdbi, String name, String owner) {
        jdbi.useHandle(h -> h
                .createUpdate("INSERT INTO lists (name, owner) VALUES (:name, :owner)")
                .bind("name", name)
                .bind("owner", owner)
                .execute());
        return jdbi.withHandle(h -> h
                .createQuery("SELECT id FROM lists WHERE name = :name")
                .bind("name", name)
                .mapTo(String.class)
                .one());
    }

    private String ownerIdOf(Jdbi jdbi, String listId) {
        return jdbi.withHandle(h -> h
                .createQuery("SELECT owner_id FROM lists WHERE id = CAST(:id AS uuid)")
                .bind("id", listId)
                .mapTo(String.class)
                .findOne()
                .orElse(null));
    }

    /** Reads V4's SQL resource verbatim off the classpath and executes it as-is. */
    private void runBackfillMigration(Jdbi jdbi) throws Exception {
        String sql;
        try (InputStream in = TodoApiIntegrationTest.class
                .getResourceAsStream("/db/migration/V4__backfill_list_owner_id.sql")) {
            assertNotNull(in, "V4 migration resource must be on the classpath");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        jdbi.useHandle(h -> h.execute(sql));
    }

    // -- HTTP-level: PATCH/POST /api/todo/lists ownerId behavior ------------

    @Test
    void patchListOwnerIdSetsOwnerIdAndDenormalizedOwnerNameAndNullClearsBoth() throws Exception {
        Jdbi jdbi = Jdbi.create(pg.getPostgresDatabase());
        String targetUserId = insertTestUser(jdbi, "Owner Target", "owner-target-http");

        ListRow list = todo.insertList("HTTP owner test");

        HttpResponse<String> setRes = httpPatch("/lists/" + list.id(),
                "{\"ownerId\":\"" + targetUserId + "\"}");
        assertEquals(200, setRes.statusCode(), "a valid ownerId should be accepted: " + setRes.body());
        JsonObject setBody = JsonParser.parseString(setRes.body()).getAsJsonObject().getAsJsonObject("list");
        assertEquals(targetUserId, setBody.get("ownerId").getAsString());
        assertEquals("Owner Target", setBody.get("owner").getAsString(),
                "ownerId writes MUST also denormalize the resolved name into owner");

        HttpResponse<String> clearRes = httpPatch("/lists/" + list.id(), "{\"ownerId\":null}");
        assertEquals(200, clearRes.statusCode());
        JsonObject clearedBody = JsonParser.parseString(clearRes.body()).getAsJsonObject().getAsJsonObject("list");
        assertTrue(clearedBody.get("ownerId").isJsonNull(), "ownerId:null must clear owner_id");
        assertTrue(clearedBody.get("owner").isJsonNull(), "ownerId:null must also clear the denormalized owner");

        todo.deleteList(list.id());
    }

    @Test
    void patchListOwnerIdWinsOverLegacyOwnerInTheSameBody() throws Exception {
        Jdbi jdbi = Jdbi.create(pg.getPostgresDatabase());
        String targetUserId = insertTestUser(jdbi, "Real Owner", "owner-wins-http");

        ListRow list = todo.insertList("HTTP owner precedence test");

        HttpResponse<String> res = httpPatch("/lists/" + list.id(),
                "{\"ownerId\":\"" + targetUserId + "\",\"owner\":\"Some Stale Text\"}");
        assertEquals(200, res.statusCode());
        JsonObject body = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonObject("list");
        assertEquals(targetUserId, body.get("ownerId").getAsString());
        assertEquals("Real Owner", body.get("owner").getAsString(),
                "ownerId must win over a legacy owner key present in the same request");

        todo.deleteList(list.id());
    }

    @Test
    void patchListOwnerIdMalformedOrUnknownReturns400NeverA500() throws Exception {
        ListRow list = todo.insertList("HTTP bad owner test");

        HttpResponse<String> malformed = httpPatch("/lists/" + list.id(), "{\"ownerId\":\"not-a-uuid\"}");
        assertEquals(400, malformed.statusCode(), "a malformed ownerId must be a 400, not a 500");

        HttpResponse<String> unknown = httpPatch("/lists/" + list.id(),
                "{\"ownerId\":\"" + UUID.randomUUID() + "\"}");
        assertEquals(400, unknown.statusCode(),
                "an ownerId with no matching user must be a 400 (validated before the FK), not a 500");

        todo.deleteList(list.id());
    }

    @Test
    void createListAcceptsOwnerIdAndResolvesTheOwnerName() throws Exception {
        Jdbi jdbi = Jdbi.create(pg.getPostgresDatabase());
        String targetUserId = insertTestUser(jdbi, "Creator Owner", "owner-create-http");

        HttpResponse<String> res = httpPost("/lists",
                "{\"name\":\"HTTP create with ownerId\",\"ownerId\":\"" + targetUserId + "\"}");
        assertEquals(200, res.statusCode(), res.body());
        JsonObject body = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonObject("list");
        assertEquals(targetUserId, body.get("ownerId").getAsString());
        assertEquals("Creator Owner", body.get("owner").getAsString());

        todo.deleteList(body.get("id").getAsString());
    }

    @Test
    void createListWithMalformedOwnerIdReturns400NeverA500() throws Exception {
        HttpResponse<String> res = httpPost("/lists",
                "{\"name\":\"HTTP create bad owner\",\"ownerId\":\"not-a-uuid\"}");
        assertEquals(400, res.statusCode());
    }

    /**
     * The items-side twin of the ownerId tests above (issue #61). The item
     * assignee reader used to accept ANY non-empty string, so a bogus id got as
     * far as the INSERT and came back as a 500 from a raw foreign-key
     * violation. Lists had already been fixed; items had not.
     */
    @Test
    void createItemWithMalformedOrUnknownAssigneeIdReturns400NeverA500() throws Exception {
        ListRow list = todo.insertList("HTTP bad assignee create test");

        String malformedBody = "{\"listId\":\"" + list.id() + "\",\"text\":\"x\","
                + "\"description\":null,\"location\":null,\"assigneeId\":\"not-a-uuid\"}";
        assertEquals(400, httpPost("/items", malformedBody).statusCode(),
                "a malformed assigneeId must be a 400, not a 500");

        String unknownBody = "{\"listId\":\"" + list.id() + "\",\"text\":\"x\","
                + "\"description\":null,\"location\":null,\"assigneeId\":\"" + UUID.randomUUID() + "\"}";
        assertEquals(400, httpPost("/items", unknownBody).statusCode(),
                "an assigneeId with no matching user must be a 400 (validated before the FK), not a 500");

        // An explicit null is still the valid way to say "unassigned".
        String nullBody = "{\"listId\":\"" + list.id() + "\",\"text\":\"unassigned\","
                + "\"description\":null,\"location\":null,\"assigneeId\":null}";
        assertEquals(200, httpPost("/items", nullBody).statusCode());

        todo.deleteList(list.id());
    }

    @Test
    void patchItemWithMalformedOrUnknownAssigneeIdReturns400NeverA500() throws Exception {
        ListRow list = todo.insertList("HTTP bad assignee patch test");
        ItemRow item = todo.insertItem(new NewItem(list.id(), "assign me", null,
                "NOT_STARTED", null, null, null, null, null));

        assertEquals(400, httpPatch("/items/" + item.id(), "{\"assigneeId\":\"not-a-uuid\"}").statusCode(),
                "a malformed assigneeId must be a 400, not a 500");
        assertEquals(400, httpPatch("/items/" + item.id(),
                        "{\"assigneeId\":\"" + UUID.randomUUID() + "\"}").statusCode(),
                "an unknown assigneeId must be a 400, not a 500");

        // A real user still assigns, so the stricter reader did not close the
        // door on the only case that matters.
        Jdbi jdbi = Jdbi.create(pg.getPostgresDatabase());
        String realUserId = insertTestUser(jdbi, "Assignee Real", "assignee-patch-http");
        HttpResponse<String> ok = httpPatch("/items/" + item.id(),
                "{\"assigneeId\":\"" + realUserId + "\"}");
        assertEquals(200, ok.statusCode(), ok.body());
        assertEquals(realUserId, JsonParser.parseString(ok.body()).getAsJsonObject()
                .getAsJsonObject("item").get("assigneeId").getAsString());

        todo.deleteList(list.id());
    }
}
