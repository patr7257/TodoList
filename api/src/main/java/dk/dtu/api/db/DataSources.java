package dk.dtu.api.db;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Builds a HikariCP pooled DataSource from a jdbc:postgresql:// URL. The URL is
 * expected to be already normalized by {@link dk.dtu.api.ApiConfig}. Any query
 * string on the URL (for example {@code ?sslmode=require} that Neon needs) is
 * passed straight through to the Postgres driver.
 */
public final class DataSources {

    private DataSources() {
    }

    public static HikariDataSource fromJdbcUrl(String jdbcUrl) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setPoolName("todolist-api");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(1);
        // Fail fast if the database is unreachable at startup.
        config.setInitializationFailTimeout(10_000);
        return new HikariDataSource(config);
    }
}
