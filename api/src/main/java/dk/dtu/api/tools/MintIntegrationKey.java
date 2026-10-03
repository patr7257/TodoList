package dk.dtu.api.tools;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

import dk.dtu.api.auth.IntegrationKeys;

/**
 * Mints one integration key for TODO_INTEGRATION_KEYS. Prompts for the
 * integration name and the TodoList user id it acts as, then prints the key
 * ONCE (for the client app's secret env) and the {@code name:sha256hex:userId}
 * entry for Dokploy. Nothing is written to disk and the key is never stored
 * anywhere by this tool, so a lost key is replaced by minting a new one.
 *
 * <p>Run from the repo root (one line, any shell):
 * {@code mvn -q -pl api -am -DskipTests package; java -cp api/target/todolist-api.jar dk.dtu.api.tools.MintIntegrationKey}
 */
public final class MintIntegrationKey {

    static final int KEY_BYTES = 32;

    private MintIntegrationKey() {
    }

    public static void main(String[] args) throws IOException {
        Console console = System.console();
        BufferedReader reader = console == null
                ? new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))
                : null;
        String name = readLine(console, reader, "Integration name (lowercase, for example bartender): ");
        String userId = readLine(console, reader, "TodoList user id (uuid) the integration acts as: ");

        String key = newKey(new SecureRandom());
        String entry;
        try {
            entry = envEntry(name, key, userId);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }
        System.out.println();
        System.out.println("Integration key. Shown ONCE and stored nowhere: put it in the client app's secret env now.");
        System.out.println(key);
        System.out.println();
        System.out.println("Entry for TODO_INTEGRATION_KEYS in Dokploy (append with a comma if it already has entries):");
        System.out.println(entry);
    }

    static String newKey(SecureRandom rng) {
        byte[] bytes = new byte[KEY_BYTES];
        rng.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String envEntry(String name, String key, String userId) {
        String n = name == null ? "" : name.trim();
        String u = userId == null ? "" : userId.trim().toLowerCase(Locale.ROOT);
        String entry = n + ":" + IntegrationKeys.sha256Hex(key) + ":" + u;
        if (IntegrationKeys.parse(entry).size() != 1) {
            throw new IllegalArgumentException("Refusing: the name must be 1 to 32 characters of a-z, 0-9 and hyphen "
                    + "(starting with a letter or digit), and the user id must be a uuid.");
        }
        return entry;
    }

    private static String readLine(Console console, BufferedReader reader, String prompt) throws IOException {
        if (console != null) {
            String s = console.readLine(prompt);
            return s == null ? "" : s;
        }
        System.out.print(prompt);
        System.out.flush();
        String s = reader.readLine();
        return s == null ? "" : s;
    }
}
