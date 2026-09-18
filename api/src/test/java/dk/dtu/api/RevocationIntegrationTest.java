package dk.dtu.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.OptionalInt;
import java.util.UUID;

import javax.sql.DataSource;

import dk.dtu.api.auth.Token;
import dk.dtu.api.db.Migrations;
import dk.dtu.api.domain.TodoService;
import dk.dtu.api.web.ApiServer;
import dk.dtu.api.web.Backend;

import io.javalin.Javalin;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Per-user session revocation (issue #74), end to end against a real (embedded)
 * Postgres and a real Javalin instance: V10's {@code users.token_version}, the
 * {@code tv} claim, and AuthFilter's comparison.
 *
 * <p>Every test that bumps a version creates its OWN user, so the tests are
 * independent and need no @Order: revocation is per user by definition, and a
 * test that revoked a shared fixture would silently decide another test's
 * outcome, which is the exact bug class this feature is about.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RevocationIntegrationTest {

    private EmbeddedPostgres pg;
    private Jdbi jdbi;
    private Token token;
    private Javalin app;
    private String baseUrl;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    void startDatabase() throws IOException {
        pg = EmbeddedPostgres.builder().start();
        DataSource ds = pg.getPostgresDatabase();
        Migrations.migrate(ds);

        jdbi = Jdbi.create(ds);
        TodoService todo = new TodoService(jdbi);
        token = new Token("revocation-secret");

        Backend backend = new Backend(ApiConfig.of(0, null, "revocation-secret"), todo, token);
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

    private String insertUser(String emailLocalPart) {
        jdbi.useHandle(h -> h
                .createUpdate("INSERT INTO users (email, name, pw_hash) VALUES (:e, :n, NULL)")
                .bind("e", emailLocalPart + "@example.com")
                .bind("n", emailLocalPart)
                .execute());
        return jdbi.withHandle(h -> h
                .createQuery("SELECT id FROM users WHERE email = :e")
                .bind("e", emailLocalPart + "@example.com")
                .mapTo(String.class)
                .one());
    }

    private int storedVersion(String userId) {
        return jdbi.withHandle(h -> h
                .createQuery("SELECT token_version FROM users WHERE id = CAST(:id AS uuid)")
                .bind("id", userId)
                .mapTo(Integer.class)
                .one());
    }

    /** The one statement that IS the revocation mechanism for now. */
    private void revoke(String userId) {
        jdbi.useHandle(h -> h
                .createUpdate("UPDATE users SET token_version = token_version + 1 "
                        + "WHERE id = CAST(:id AS uuid)")
                .bind("id", userId)
                .execute());
    }

    private HttpResponse<String> state(String bearer) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/todo/state"))
                .timeout(Duration.ofSeconds(10))
                .GET();
        if (bearer != null) {
            req.header("Authorization", "Bearer " + bearer);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    // -- tests -----------------------------------------------------------------

    @Test
    void aFreshUserStartsAtVersionZero() {
        // V10's DEFAULT 0 is what makes the column safe to add without a
        // backfill and without a flag day.
        assertEquals(0, storedVersion(insertUser("rev-fresh")));
    }

    @Test
    void aLegacyTokenWithNoClaimStillAuthenticates() throws Exception {
        // The transition case: this is every session already sitting in a
        // browser when this deploys.
        String uid = insertUser("rev-legacy");
        HttpResponse<String> res = state(token.issue(uid));
        assertEquals(200, res.statusCode(), res.body());
    }

    @Test
    void aMatchingVersionAuthenticates() throws Exception {
        String uid = insertUser("rev-match");
        HttpResponse<String> res = state(token.issue(uid, OptionalInt.of(storedVersion(uid))));
        assertEquals(200, res.statusCode(), res.body());
    }

    @Test
    void aStaleVersionIsRejectedTheSameWayABadSignatureIs() throws Exception {
        String uid = insertUser("rev-stale");
        HttpResponse<String> stale = state(token.issue(uid, OptionalInt.of(storedVersion(uid) + 1)));
        assertEquals(401, stale.statusCode(), stale.body());

        HttpResponse<String> garbage = state("not.a-real-token");
        assertEquals(401, garbage.statusCode(), garbage.body());
        assertEquals(garbage.body(), stale.body());
    }

    @Test
    void bumpingTheVersionRevokesOnlyThatUser() throws Exception {
        String alice = insertUser("rev-alice");
        String bob = insertUser("rev-bob");
        String aliceToken = token.issue(alice, OptionalInt.of(storedVersion(alice)));
        String bobToken = token.issue(bob, OptionalInt.of(storedVersion(bob)));

        assertEquals(200, state(aliceToken).statusCode());
        assertEquals(200, state(bobToken).statusCode());

        revoke(alice);

        assertEquals(401, state(aliceToken).statusCode());
        assertEquals(200, state(bobToken).statusCode(),
                "the other user's session must survive a revocation");

        // And the revoked user signs back in: a token minted at the new version
        // works, so a revocation is not a permanent lockout.
        String reminted = token.issue(alice, OptionalInt.of(storedVersion(alice)));
        assertNotEquals(aliceToken, reminted);
        assertEquals(200, state(reminted).statusCode());
    }

    @Test
    void aLegacyTokenSurvivesARevocation() throws Exception {
        // Documenting the transition gap rather than pretending it is not
        // there: a pre-#74 token carries no claim, so there is nothing to
        // compare and AuthFilter lets it through. Tightening that branch (and
        // closing this gap) is safe only once every live session carries a tv.
        String uid = insertUser("rev-gap");
        String legacy = token.issue(uid);
        revoke(uid);
        HttpResponse<String> res = state(legacy);
        assertEquals(200, res.statusCode(), res.body());
    }

    @Test
    void aVersionClaimForAnUnknownUserIsRejected() throws Exception {
        // A deleted user's token must not outlive the row: no row means no
        // version, and no version can never match a claim.
        HttpResponse<String> res = state(token.issue(UUID.randomUUID().toString(), OptionalInt.of(0)));
        assertEquals(401, res.statusCode(), res.body());
    }

    @Test
    void theVersionClaimCannotBeStrippedToDodgeARevocation() throws Exception {
        String uid = insertUser("rev-downgrade");
        String versioned = token.issue(uid, OptionalInt.of(storedVersion(uid)));
        String legacy = token.issue(uid);
        revoke(uid);

        // Splice the claimless payload onto the versioned signature: the
        // signature covers the payload STRING, so this is just a bad token.
        String spliced = legacy.substring(0, legacy.indexOf('.'))
                + versioned.substring(versioned.indexOf('.'));
        assertTrue(spliced.contains("."));
        assertEquals(401, state(spliced).statusCode());
    }
}
