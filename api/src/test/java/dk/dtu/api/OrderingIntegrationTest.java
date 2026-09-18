package dk.dtu.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import dk.dtu.api.auth.Token;
import dk.dtu.api.db.Migrations;
import dk.dtu.api.domain.ColumnValue;
import dk.dtu.api.domain.ItemRow;
import dk.dtu.api.domain.ListRow;
import dk.dtu.api.domain.NewItem;
import dk.dtu.api.domain.TodoService;
import dk.dtu.api.web.ApiServer;
import dk.dtu.api.web.Backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.Javalin;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Per-user ordering of lists and tasks (issue #77), end to end against a real
 * (embedded) Postgres and a real Javalin instance: V9's override tables, the
 * COALESCE read path behind GET /api/todo/state, and the two bulk write routes.
 *
 * <p>Every test creates its own lists/items and asserts only on the relative
 * order of ITS OWN ids, because the todo space is shared and global: a test
 * that asserted on the whole of /state would be coupled to every other test in
 * the file. They are independent on purpose (no @Order), unlike
 * TinderIntegrationTest where the ordering is load bearing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderingIntegrationTest {

    private EmbeddedPostgres pg;
    private Jdbi jdbi;
    private TodoService todo;
    private Token token;
    private Javalin app;
    private String baseUrl;
    private final HttpClient http = HttpClient.newHttpClient();

    private String aliceId;
    private String aliceToken;
    private String bobToken;

    @BeforeAll
    void startDatabase() throws IOException {
        pg = EmbeddedPostgres.builder().start();
        DataSource ds = pg.getPostgresDatabase();
        Migrations.migrate(ds);

        jdbi = Jdbi.create(ds);
        todo = new TodoService(jdbi);
        token = new Token("ordering-secret");

        aliceId = insertUser("Alice", "alice-order");
        String bobId = insertUser("Bob", "bob-order");
        aliceToken = token.issue(aliceId);
        bobToken = token.issue(bobId);

        Backend backend = new Backend(ApiConfig.of(0, null, "ordering-secret"), todo, token);
        app = ApiServer.create(backend);
        app.start(0);
        baseUrl = "http://127.0.0.1:" + app.port();
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

    private HttpResponse<String> httpPut(String path, String body, String bearer) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/todo" + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .method("PUT", HttpRequest.BodyPublishers.ofString(body));
        if (bearer != null) {
            req.header("Authorization", "Bearer " + bearer);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonObject state(String bearer) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/todo/state"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + bearer)
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode(), res.body());
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    /** The ids of {@code wanted}, in the order this user's /state serves them. */
    private List<String> listOrderFor(String bearer, List<String> wanted) throws Exception {
        List<String> out = new ArrayList<>();
        for (JsonElement el : state(bearer).getAsJsonArray("lists")) {
            String id = el.getAsJsonObject().get("id").getAsString();
            if (wanted.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** The item ids inside one list, in the order this user's /state serves them. */
    private List<String> itemOrderFor(String bearer, String listId) throws Exception {
        List<String> out = new ArrayList<>();
        for (JsonElement el : state(bearer).getAsJsonArray("lists")) {
            JsonObject list = el.getAsJsonObject();
            if (!list.get("id").getAsString().equals(listId)) {
                continue;
            }
            for (JsonElement item : list.getAsJsonArray("items")) {
                out.add(item.getAsJsonObject().get("id").getAsString());
            }
        }
        return out;
    }

    /** The sort value this user's /state reports for one list. */
    private int listSortFor(String bearer, String listId) throws Exception {
        for (JsonElement el : state(bearer).getAsJsonArray("lists")) {
            JsonObject list = el.getAsJsonObject();
            if (list.get("id").getAsString().equals(listId)) {
                return list.get("sort").getAsInt();
            }
        }
        throw new AssertionError("list " + listId + " is missing from /state");
    }

    private int overrideRowCount(String table, String userId) {
        return jdbi.withHandle(h -> h
                .createQuery("SELECT COUNT(*) FROM " + table + " WHERE user_id = CAST(:uid AS uuid)")
                .bind("uid", userId)
                .mapTo(Integer.class)
                .one());
    }

    private static String orderBody(List<String> idsInOrder) {
        StringBuilder sb = new StringBuilder("{\"order\":[");
        for (int i = 0; i < idsInOrder.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"id\":\"").append(idsInOrder.get(i)).append("\",\"sort\":").append(i).append("}");
        }
        return sb.append("]}").toString();
    }

    private void setBaselineSort(String listId, int sort) {
        todo.updateList(listId, List.of(new ColumnValue("sort", ":sort", sort, Types.INTEGER)));
    }

    private ItemRow newItem(String listId, String text) {
        return todo.insertItem(new NewItem(listId, text, null, "NOT_STARTED", null, null, null, null, aliceId));
    }

    // -- tests -----------------------------------------------------------------

    @Test
    void twoUsersHoldDifferentOrderingsOfTheSameListsAtTheSameTime() throws Exception {
        ListRow x = todo.insertList("order-x");
        ListRow y = todo.insertList("order-y");
        ListRow z = todo.insertList("order-z");
        List<String> ids = List.of(x.id(), y.id(), z.id());

        HttpResponse<String> alice = httpPut("/lists/order", orderBody(List.of(z.id(), y.id(), x.id())), aliceToken);
        assertEquals(200, alice.statusCode(), alice.body());
        JsonObject aliceOut = JsonParser.parseString(alice.body()).getAsJsonObject();
        assertTrue(aliceOut.get("ok").getAsBoolean());
        assertEquals(3, aliceOut.get("count").getAsInt());

        assertEquals(200, httpPut("/lists/order", orderBody(List.of(y.id(), x.id(), z.id())), bobToken).statusCode());

        assertEquals(List.of(z.id(), y.id(), x.id()), listOrderFor(aliceToken, ids),
                "Alice must see her own arrangement");
        assertEquals(List.of(y.id(), x.id(), z.id()), listOrderFor(bobToken, ids),
                "Bob must see his own arrangement, not Alice's");

        todo.deleteList(x.id());
        todo.deleteList(y.id());
        todo.deleteList(z.id());
    }

    @Test
    void twoUsersHoldDifferentOrderingsOfTheSameItemsAtTheSameTime() throws Exception {
        ListRow list = todo.insertList("order-items");
        ItemRow a = newItem(list.id(), "a");
        ItemRow b = newItem(list.id(), "b");
        ItemRow c = newItem(list.id(), "c");

        assertEquals(200, httpPut("/items/order", orderBody(List.of(c.id(), a.id(), b.id())), aliceToken).statusCode());
        assertEquals(200, httpPut("/items/order", orderBody(List.of(b.id(), c.id(), a.id())), bobToken).statusCode());

        assertEquals(List.of(c.id(), a.id(), b.id()), itemOrderFor(aliceToken, list.id()));
        assertEquals(List.of(b.id(), c.id(), a.id()), itemOrderFor(bobToken, list.id()));

        todo.deleteList(list.id());
    }

    @Test
    void aUserWhoNeverReorderedStillSeesTheBaselineSortColumn() throws Exception {
        ListRow p = todo.insertList("baseline-p");
        ListRow q = todo.insertList("baseline-q");
        setBaselineSort(p.id(), 50);
        setBaselineSort(q.id(), 10);
        List<String> ids = List.of(p.id(), q.id());
        String carolId = insertUser("Carol", "carol-baseline");
        String carolToken = token.issue(carolId);

        // Alice reorders; Carol never has.
        assertEquals(200, httpPut("/lists/order", orderBody(List.of(p.id(), q.id())), aliceToken).statusCode());

        assertEquals(List.of(q.id(), p.id()), listOrderFor(carolToken, ids),
                "a user with no override rows must be served the baseline lists.sort order");
        assertEquals(10, listSortFor(carolToken, q.id()),
                "and the baseline value itself, not somebody else's override");
        assertEquals(50, listSortFor(carolToken, p.id()));

        // The override is served through the SAME sort field for the user who
        // does have one, which is what keeps the payload shape unchanged.
        assertEquals(List.of(p.id(), q.id()), listOrderFor(aliceToken, ids));
        assertEquals(0, listSortFor(aliceToken, p.id()));
        assertEquals(1, listSortFor(aliceToken, q.id()));
        assertNotEquals(listSortFor(aliceToken, p.id()), listSortFor(carolToken, p.id()));

        todo.deleteList(p.id());
        todo.deleteList(q.id());
    }

    @Test
    void anUnknownIdAnywhereInTheBatchWritesNothingAtAll() throws Exception {
        ListRow one = todo.insertList("atomic-1");
        ListRow two = todo.insertList("atomic-2");
        setBaselineSort(one.id(), 1);
        setBaselineSort(two.id(), 2);
        List<String> ids = List.of(one.id(), two.id());
        String daveId = insertUser("Dave", "dave-atomic");
        String daveToken = token.issue(daveId);

        String body = "{\"order\":["
                + "{\"id\":\"" + two.id() + "\",\"sort\":0},"
                + "{\"id\":\"" + UUID.randomUUID() + "\",\"sort\":1},"
                + "{\"id\":\"" + one.id() + "\",\"sort\":2}]}";
        HttpResponse<String> res = httpPut("/lists/order", body, daveToken);
        assertEquals(404, res.statusCode(), res.body());

        assertEquals(0, overrideRowCount("list_order", daveId),
                "the valid first row must NOT have landed: the whole batch is one transaction");
        assertEquals(List.of(one.id(), two.id()), listOrderFor(daveToken, ids),
                "Dave still sees the baseline order, so no partial arrangement survived");

        // Same for a malformed row in the middle, which is rejected earlier (400)
        // but must leave exactly as little behind.
        String malformed = "{\"order\":["
                + "{\"id\":\"" + two.id() + "\",\"sort\":0},"
                + "{\"id\":\"not-a-uuid\",\"sort\":1}]}";
        assertEquals(400, httpPut("/lists/order", malformed, daveToken).statusCode());
        assertEquals(0, overrideRowCount("list_order", daveId));

        // The same batch without the bad row lands whole, so the rejection above
        // was about the bad row and not about the request being unusable.
        assertEquals(200, httpPut("/lists/order", orderBody(List.of(two.id(), one.id())), daveToken).statusCode());
        assertEquals(List.of(two.id(), one.id()), listOrderFor(daveToken, ids));

        todo.deleteList(one.id());
        todo.deleteList(two.id());
    }

    @Test
    void anUnknownItemIdAlsoWritesNothingAtAll() throws Exception {
        ListRow list = todo.insertList("atomic-items");
        ItemRow a = newItem(list.id(), "a");
        ItemRow b = newItem(list.id(), "b");
        String eveId = insertUser("Eve", "eve-atomic-items");
        String eveToken = token.issue(eveId);

        String body = "{\"order\":["
                + "{\"id\":\"" + b.id() + "\",\"sort\":0},"
                + "{\"id\":\"" + UUID.randomUUID() + "\",\"sort\":1},"
                + "{\"id\":\"" + a.id() + "\",\"sort\":2}]}";
        assertEquals(404, httpPut("/items/order", body, eveToken).statusCode());
        assertEquals(0, overrideRowCount("item_order", eveId));
        assertEquals(List.of(a.id(), b.id()), itemOrderFor(eveToken, list.id()));

        todo.deleteList(list.id());
    }

    @Test
    void oneUserCannotWriteIntoAnotherUsersOrdering() throws Exception {
        ListRow m = todo.insertList("theirs-m");
        ListRow n = todo.insertList("theirs-n");
        List<String> ids = List.of(m.id(), n.id());
        String frankId = insertUser("Frank", "frank-owner");
        String graceId = insertUser("Grace", "grace-attacker");
        String frankToken = token.issue(frankId);
        String graceToken = token.issue(graceId);

        assertEquals(200, httpPut("/lists/order", orderBody(List.of(m.id(), n.id())), frankToken).statusCode());

        // Grace names Frank in the body every way a client could: a top-level
        // userId, a per-entry userId, and the snake_case spelling of both. The
        // caller is the TOKEN, so all of it is ignored.
        String hostile = "{\"userId\":\"" + frankId + "\",\"user_id\":\"" + frankId + "\",\"order\":["
                + "{\"id\":\"" + n.id() + "\",\"sort\":0,\"userId\":\"" + frankId + "\"},"
                + "{\"id\":\"" + m.id() + "\",\"sort\":1,\"user_id\":\"" + frankId + "\"}]}";
        assertEquals(200, httpPut("/lists/order", hostile, graceToken).statusCode());

        assertEquals(List.of(m.id(), n.id()), listOrderFor(frankToken, ids),
                "Frank's arrangement must be untouched by Grace's request");
        assertEquals(List.of(n.id(), m.id()), listOrderFor(graceToken, ids),
                "Grace's own arrangement is the only thing her request changed");

        List<String> frankRows = jdbi.withHandle(h -> h
                .createQuery("SELECT list_id FROM list_order WHERE user_id = CAST(:uid AS uuid) ORDER BY sort")
                .bind("uid", frankId)
                .mapTo(String.class)
                .list());
        assertEquals(List.of(m.id(), n.id()), frankRows,
                "and the override rows themselves are still Frank's own");

        todo.deleteList(m.id());
        todo.deleteList(n.id());
    }

    @Test
    void anEmptyOrderIsAnAcceptedNoOp() throws Exception {
        String hankId = insertUser("Hank", "hank-empty");
        HttpResponse<String> res = httpPut("/lists/order", "{\"order\":[]}", token.issue(hankId));
        assertEquals(200, res.statusCode(), res.body());
        assertEquals(0, JsonParser.parseString(res.body()).getAsJsonObject().get("count").getAsInt());
        assertEquals(0, overrideRowCount("list_order", hankId));
    }

    @Test
    void bothReorderRoutesRequireAuthentication() throws Exception {
        // The new routes are NOT on AuthFilter's allowlist, which is the whole
        // reason they needed no change there. If either ever became public,
        // this is what would notice.
        ListRow list = todo.insertList("auth-order");
        String body = orderBody(List.of(list.id()));

        assertEquals(401, httpPut("/lists/order", body, null).statusCode());
        assertEquals(401, httpPut("/items/order", body, null).statusCode());
        assertEquals(401, httpPut("/lists/order", body, "not-a-real-token").statusCode());

        todo.deleteList(list.id());
    }

    @Test
    void reorderingDoesNotChangeTheStatePayloadShape() throws Exception {
        // The design's headline claim: the override is served through the
        // EXISTING sort field, so /state gains no key. ViewsTest pins the key
        // order from the unit side; this pins it on the wire, after a reorder.
        ListRow list = todo.insertList("shape-check");
        assertEquals(200, httpPut("/lists/order", orderBody(List.of(list.id())), aliceToken).statusCode());

        JsonArray lists = state(aliceToken).getAsJsonArray("lists");
        JsonObject mine = null;
        for (JsonElement el : lists) {
            if (el.getAsJsonObject().get("id").getAsString().equals(list.id())) {
                mine = el.getAsJsonObject();
            }
        }
        assertTrue(mine != null, "the reordered list must still be in /state");
        assertEquals(List.of("id", "name", "sort", "createdAt", "owner", "priority", "year",
                        "location", "description", "taskColumnsJson", "ownerId", "ownerName",
                        "completionPercentage", "items"),
                List.copyOf(mine.keySet()),
                "GET /state's list object must be the same shape after a reorder");

        todo.deleteList(list.id());
    }
}
