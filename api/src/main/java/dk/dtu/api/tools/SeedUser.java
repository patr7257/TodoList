package dk.dtu.api.tools;

import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

import dk.dtu.api.ApiConfig;
import dk.dtu.api.auth.Scrypt;
import dk.dtu.api.db.DataSources;

/**
 * One-off command-line seeder that creates (or updates) a login account in the
 * shared Postgres {@code users} table, using the SAME scrypt hashing the API's
 * login expects, so a seeded user can sign in on the desktop client and the web.
 *
 * <p>Run it through {@code scripts/seed-user.ps1} (or {@code seed-user.sh}),
 * which builds the api jar and supplies {@code DATABASE_URL}. It reads the Neon
 * connection string from the {@code DATABASE_URL} environment variable and
 * prompts for email, name, and password on the console (password hidden when a
 * real console is attached). Upserts on the unique email, so re-running just
 * resets that user's name/password.
 */
public final class SeedUser {

    private SeedUser() {
    }

    public static void main(String[] args) throws Exception {
        String raw = System.getenv("DATABASE_URL");
        if (raw == null || raw.isBlank()) {
            System.err.println("DATABASE_URL is not set. Provide the Neon (unpooled) connection string.");
            System.exit(2);
            return;
        }

        Console console = System.console();
        BufferedReader reader = console == null
                ? new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))
                : null;

        String email = readLine(console, reader, "Email: ");
        String name = readLine(console, reader, "Name: ");
        String password = readSecret(console, reader, "Password: ");

        if (email == null || email.isBlank() || name == null || name.isBlank()
                || password == null || password.isEmpty()) {
            System.err.println("Email, name, and password are all required.");
            System.exit(2);
            return;
        }

        String hash = Scrypt.hash(password);
        DataSources.Parsed db = DataSources.parse(ApiConfig.normalizeJdbcUrl(raw));

        String sql = "INSERT INTO users (email, name, pw_hash) VALUES (?, ?, ?) "
                + "ON CONFLICT (email) DO UPDATE SET name = EXCLUDED.name, pw_hash = EXCLUDED.pw_hash";
        try (Connection conn = DriverManager.getConnection(db.url(), db.username(), db.password());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            ps.setString(2, name.trim());
            ps.setString(3, hash);
            ps.executeUpdate();
        }

        System.out.println("Seeded user: " + email.trim()
                + " (sign in with this exact email and the password you entered).");
    }

    private static String readLine(Console console, BufferedReader reader, String prompt) throws Exception {
        if (console != null) {
            return console.readLine(prompt);
        }
        System.out.print(prompt);
        System.out.flush();
        return reader.readLine();
    }

    private static String readSecret(Console console, BufferedReader reader, String prompt) throws Exception {
        if (console != null) {
            char[] chars = console.readPassword(prompt);
            return chars == null ? null : new String(chars);
        }
        // No console (for example run under a build tool): fall back to a plain
        // read. The password will echo in that case.
        System.out.print(prompt);
        System.out.flush();
        return reader.readLine();
    }
}
