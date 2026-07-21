package dk.dtu.api.db;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Builds a HikariCP pooled DataSource from a jdbc:postgresql:// URL. Neon and
 * Vercel hand out libpq-style URLs that carry the credentials inline as
 * {@code //user:password@host/db}. The Postgres JDBC driver does NOT accept
 * userinfo in the URL (it tries to read the password as the port and fails with
 * "invalid port number" / "No suitable driver"), so we split any inline
 * credentials out here and pass them to Hikari as username/password, leaving a
 * clean {@code jdbc:postgresql://host[:port]/db?query} URL. Any query string
 * (for example {@code ?sslmode=require} that Neon needs) is preserved.
 */
public final class DataSources {

    private DataSources() {
    }

    /** A JDBC URL split into a credential-free URL plus optional username/password. */
    public record Parsed(String url, String username, String password) {
    }

    public static HikariDataSource fromJdbcUrl(String jdbcUrl) {
        Parsed parsed = parse(jdbcUrl);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(parsed.url());
        if (parsed.username() != null) {
            config.setUsername(parsed.username());
        }
        if (parsed.password() != null) {
            config.setPassword(parsed.password());
        }
        config.setPoolName("todolist-api");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(1);
        // Fail fast if the database is unreachable at startup.
        config.setInitializationFailTimeout(10_000);
        return new HikariDataSource(config);
    }

    /**
     * Splits inline {@code user:password@} credentials out of a
     * jdbc:postgresql:// URL. When the URL has no inline credentials it is
     * returned unchanged with null username/password. A URL that cannot be
     * parsed is returned as-is so the driver reports a clear error.
     */
    public static Parsed parse(String jdbcUrl) {
        if (jdbcUrl == null) {
            return new Parsed(null, null, null);
        }
        if (!jdbcUrl.startsWith("jdbc:")) {
            return new Parsed(jdbcUrl, null, null);
        }
        String withoutPrefix = jdbcUrl.substring("jdbc:".length());
        URI uri;
        try {
            uri = new URI(withoutPrefix);
        } catch (Exception e) {
            return new Parsed(jdbcUrl, null, null);
        }
        String userInfo = uri.getRawUserInfo();
        if (userInfo == null || userInfo.isEmpty() || uri.getHost() == null) {
            return new Parsed(jdbcUrl, null, null);
        }

        String username;
        String password = null;
        int colon = userInfo.indexOf(':');
        if (colon >= 0) {
            username = decode(userInfo.substring(0, colon));
            password = decode(userInfo.substring(colon + 1));
        } else {
            username = decode(userInfo);
        }

        StringBuilder clean = new StringBuilder("jdbc:");
        clean.append(uri.getScheme()).append("://").append(uri.getHost());
        if (uri.getPort() != -1) {
            clean.append(':').append(uri.getPort());
        }
        if (uri.getRawPath() != null) {
            clean.append(uri.getRawPath());
        }
        if (uri.getRawQuery() != null) {
            clean.append('?').append(uri.getRawQuery());
        }
        return new Parsed(clean.toString(), username, password);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
