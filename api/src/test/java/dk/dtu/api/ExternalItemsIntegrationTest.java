package dk.dtu.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.sql.DataSource;

import dk.dtu.api.db.Migrations;
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

    @BeforeAll
    void startDatabase() throws IOException {
        pg = EmbeddedPostgres.builder().start();
        ds = pg.getPostgresDatabase();
        Migrations.migrate(ds);
        jdbi = Jdbi.create(ds);
        todo = new TodoService(jdbi);
        aliceId = insertUser("Alice", "alice-ext");
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
}
