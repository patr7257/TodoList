package dk.dtu.api;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import dk.dtu.api.auth.AuthService;
import dk.dtu.api.auth.Token;
import dk.dtu.api.db.DataSources;
import dk.dtu.api.db.Migrations;
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
        RateLimiter loginLimiter = new RateLimiter(config.rateLimitMax(), config.rateLimitWindowSeconds());

        if (!config.databaseConfigured()) {
            log.warn("DATABASE_URL is not set: data routes will answer 503 until it is configured.");
            return new Backend(config, null, null, token, loginLimiter);
        }

        HikariDataSource dataSource = DataSources.fromJdbcUrl(config.databaseUrl());
        Migrations.migrate(dataSource);
        return backendFor(config, dataSource, token, loginLimiter);
    }

    /**
     * Builds a fully wired Backend over an existing DataSource. Migrations are
     * the caller's responsibility. Used by tests with embedded Postgres.
     */
    public static Backend backendFor(ApiConfig config, DataSource dataSource, Token token,
                                     RateLimiter loginLimiter) {
        Jdbi jdbi = Jdbi.create(dataSource);
        TodoService todo = new TodoService(jdbi);
        AuthService auth = new AuthService(todo, token);
        return new Backend(config, todo, auth, token, loginLimiter);
    }
}
