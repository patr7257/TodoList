package dk.dtu.api.tools;

import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

import dk.dtu.api.ApiConfig;
import dk.dtu.api.db.DataSources;

/**
 * Creates (or renames) an account in the shared Postgres {@code users} table.
 *
 * <p>THIS IS THE ONLY WAY AN ACCOUNT COMES INTO EXISTENCE. Nothing in the
 * product self-signs-up, by design: the website's magic-link route mails a link
 * only to an address that already has a {@code users} row AND is on an explicit
 * allowlist (website/src/lib/todo/allowlist.ts), and the passkey path is
 * usernameless, so it can only resolve a credential that was enrolled from an
 * existing session. Retiring password login (issue #61) therefore deleted
 * {@code AuthService} and {@code Scrypt} but deliberately kept this tool:
 * deleting it as well would have left a product with no way to add a person.
 *
 * <p>The row it writes is PASSWORDLESS: {@code pw_hash} is inserted as NULL,
 * which V7 made legal. There is no password to choose because there is no
 * password form anywhere any more. The new account signs in by asking for a
 * magic link at its email address, and can enrol a passkey once that first
 * session exists.
 *
 * <p>Two consequences worth knowing before running it. First, the email must
 * also be on the website's allowlist or the magic link is never mailed, and the
 * response looks identical to a successful request (that uniformity is
 * deliberate, so a stranger cannot probe for members). Second, the upsert only
 * touches {@code name}: re-running for an existing email renames the person and
 * leaves any enrolled passkeys and any existing {@code pw_hash} value alone, so
 * a fix to a typo can never lock somebody out.
 *
 * <p>Run it through {@code scripts/seed-user.ps1} (or {@code seed-user.sh}),
 * which builds the api jar and supplies {@code DATABASE_URL}. It reads the Neon
 * connection string from the {@code DATABASE_URL} environment variable and
 * prompts for email and name on the console.
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

        if (email == null || email.isBlank() || name == null || name.isBlank()) {
            System.err.println("Email and name are both required.");
            System.exit(2);
            return;
        }

        DataSources.Parsed db = DataSources.parse(ApiConfig.normalizeJdbcUrl(raw));

        // pw_hash stays NULL: there is no password path to feed it, and the
        // column survives only as legacy data nothing reads. DO NOT extend the
        // ON CONFLICT clause to touch it, or re-running this to fix a name
        // would silently clear an old row's value.
        String sql = "INSERT INTO users (email, name, pw_hash) VALUES (?, ?, NULL) "
                + "ON CONFLICT (email) DO UPDATE SET name = EXCLUDED.name";
        try (Connection conn = DriverManager.getConnection(db.url(), db.username(), db.password());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            ps.setString(2, name.trim());
            ps.executeUpdate();
        }

        System.out.println("Account ready: " + email.trim());
        System.out.println("Sign in at the website with a magic link to this exact address, "
                + "then enrol a passkey from the signed-in session.");
        System.out.println("The address must also be on TODO_AUTH_ALLOWED_EMAILS "
                + "(or the built-in default pair) or no link is sent.");
    }

    private static String readLine(Console console, BufferedReader reader, String prompt) throws Exception {
        if (console != null) {
            return console.readLine(prompt);
        }
        System.out.print(prompt);
        System.out.flush();
        return reader.readLine();
    }
}
