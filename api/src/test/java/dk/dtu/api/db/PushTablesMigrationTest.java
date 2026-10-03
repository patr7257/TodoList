package dk.dtu.api.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * V12 (push_subscriptions, notification_prefs). The Java API never reads these
 * tables; the website does, with exactly the statements exercised here: an
 * upsert on endpoint that re-keys the owner, and one preference row per user
 * and type. Each test uses its own users and endpoints.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PushTablesMigrationTest {

    private EmbeddedPostgres pg;
    private Jdbi jdbi;

    @BeforeAll
    void startDatabase() throws IOException {
        pg = EmbeddedPostgres.builder().start();
        Migrations.migrate(pg.getPostgresDatabase());
        jdbi = Jdbi.create(pg.getPostgresDatabase());
    }

    @AfterAll
    void stopDatabase() throws IOException {
        if (pg != null) {
            pg.close();
        }
    }

    private String insertUser(String emailLocalPart) {
        return jdbi.withHandle(h -> h
                .createQuery("INSERT INTO users (email, name, pw_hash) VALUES (:e, :n, NULL) RETURNING id::text")
                .bind("e", emailLocalPart + "@example.com")
                .bind("n", emailLocalPart)
                .mapTo(String.class)
                .one());
    }

    private void upsertSubscription(String userId, String endpoint) {
        jdbi.useHandle(h -> h.createUpdate("""
                INSERT INTO push_subscriptions (user_id, endpoint, p256dh, auth)
                VALUES (:u::uuid, :e, 'p', 'a')
                ON CONFLICT (endpoint) DO UPDATE
                   SET user_id = excluded.user_id, updated_at = now()
                """)
                .bind("u", userId)
                .bind("e", endpoint)
                .execute());
    }

    private String ownerOf(String endpoint) {
        return jdbi.withHandle(h -> h
                .createQuery("SELECT user_id::text FROM push_subscriptions WHERE endpoint = :e")
                .bind("e", endpoint)
                .mapTo(String.class)
                .one());
    }

    @Test
    void subscribeUpsertsOnEndpointAndRekeysTheOwner() {
        String alice = insertUser("push-alice");
        String bob = insertUser("push-bob");
        String endpoint = "https://push.example/shared-device";

        upsertSubscription(alice, endpoint);
        upsertSubscription(bob, endpoint);

        int rows = jdbi.withHandle(h -> h
                .createQuery("SELECT count(*) FROM push_subscriptions WHERE endpoint = :e")
                .bind("e", endpoint)
                .mapTo(Integer.class)
                .one());
        assertEquals(1, rows);
        assertEquals(bob, ownerOf(endpoint));
    }

    @Test
    void deletingAUserTakesTheirSubscriptionsAndPrefs() {
        String carol = insertUser("push-carol");
        upsertSubscription(carol, "https://push.example/carol-phone");
        jdbi.useHandle(h -> h
                .createUpdate("INSERT INTO notification_prefs (user_id, type, enabled) VALUES (:u::uuid, 'assigned', false)")
                .bind("u", carol)
                .execute());

        jdbi.useHandle(h -> h.createUpdate("DELETE FROM users WHERE id = :u::uuid").bind("u", carol).execute());

        int left = jdbi.withHandle(h -> h.createQuery("""
                SELECT (SELECT count(*) FROM push_subscriptions WHERE user_id = :u::uuid)
                     + (SELECT count(*) FROM notification_prefs WHERE user_id = :u::uuid)
                """).bind("u", carol).mapTo(Integer.class).one());
        assertEquals(0, left);
    }

    @Test
    void onePreferenceRowPerUserAndType() {
        String dave = insertUser("push-dave");
        String insert = "INSERT INTO notification_prefs (user_id, type, enabled) VALUES (:u::uuid, 'dueSoon', true)";
        jdbi.useHandle(h -> h.createUpdate(insert).bind("u", dave).execute());

        assertThrows(UnableToExecuteStatementException.class,
                () -> jdbi.useHandle(h -> h.createUpdate(insert).bind("u", dave).execute()));
    }
}
