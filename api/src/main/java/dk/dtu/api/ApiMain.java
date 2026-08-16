package dk.dtu.api;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import dk.dtu.api.auth.Token;
import dk.dtu.api.db.DataSources;
import dk.dtu.api.db.Migrations;
import dk.dtu.api.domain.CountersService;
import dk.dtu.api.domain.SharesService;
import dk.dtu.api.domain.TinderService;
import dk.dtu.api.domain.TodoService;
import dk.dtu.api.web.ApiServer;
import dk.dtu.api.web.Backend;
import dk.dtu.api.web.RateLimiter;

import io.javalin.Javalin;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Headless entrypoint for the TodoList API. Loads config, runs Flyway (only
 * when a database is configured), builds the DataSource / JDBI / services, and
 * starts Javalin. When DATABASE_URL is absent the server still starts and every
 * data route answers 503, matching the website's behaviour with no database.
 */
public final class ApiMain {

    private static final Logger log = LoggerFactory.getLogger(ApiMain.class);

    private ApiMain() {
    }

    public static void main(String[] args) {
        ApiConfig config = ApiConfig.fromEnvironment();
        Backend backend = buildBackend(config);

        Javalin app = ApiServer.create(backend);
        app.start(config.httpPort());

        log.info("TodoList API listening on port {} (database {}, session secret {})",
                config.httpPort(),
                config.databaseConfigured() ? "configured" : "NOT configured",
                config.sessionSecretConfigured() ? "configured" : "NOT configured");

        Runtime.getRuntime().addShutdownHook(new Thread(app::stop, "api-shutdown"));
    }

    /**
     * Wires the backend from config. When no database URL is set, returns a
     * Backend with null services so data routes answer 503.
     */
    public static Backend buildBackend(ApiConfig config) {
        Token token = new Token(config.sessionSecret());

        if (!config.databaseConfigured()) {
            log.warn("DATABASE_URL is not set: data routes will answer 503 until it is configured.");
            return new Backend(config, null, token);
        }

        HikariDataSource dataSource = DataSources.fromJdbcUrl(config.databaseUrl());
        Migrations.migrate(dataSource);
        return backendFor(config, dataSource, token);
    }

    /**
     * Builds a fully wired Backend over an existing DataSource. Migrations are
     * the caller's responsibility. Used by tests with embedded Postgres.
     */
    public static Backend backendFor(ApiConfig config, DataSource dataSource, Token token) {
        Jdbi jdbi = Jdbi.create(dataSource);
        TodoService todo = new TodoService(jdbi);
        CountersService counters = new CountersService(jdbi);
        SharesService shares = new SharesService(jdbi);
        // TinderService is handed the TodoService rather than building its own
        // item SQL: a right swipe has to create an ORDINARY item, through the
        // one method every other client's item creation already goes through.
        TinderService tinder = new TinderService(jdbi, todo);
        // The public share route is the only unauthenticated route left, and it
        // is the only one with a limiter. It needs one because it is reachable
        // without a token at all: the cap is what stops a sweep of the 192-bit
        // token space from being free to attempt.
        RateLimiter shareLimiter = new RateLimiter(
                config.shareRateLimitMax(), config.shareRateLimitWindowSeconds());
        return new Backend(config, todo, token, counters, shares, shareLimiter, tinder);
    }
}
