package dk.dtu.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The parsed {@code TODO_INTEGRATION_KEYS} setting: comma separated entries of
 * {@code name:sha256hex:userId}. Only the SHA-256 of a key is ever configured,
 * so the environment (and a leaked copy of it) never holds a usable key.
 *
 * <p>A presented key is hashed and compared against EVERY entry with
 * {@link MessageDigest#isEqual}, with no early exit, so neither the comparison
 * nor the loop leaks which entry matched or how much of a hash agreed.
 *
 * <p>A malformed entry is skipped rather than failing startup: a typo in one
 * Dokploy variable must not crash-loop the live API for the website. It is
 * reported as {@code entry N} only, never by content, so the startup log
 * cannot leak a hash or a user id.
 */
public final class IntegrationKeys {

    /** One integration as configured. {@code sha256} is the raw 32 bytes. */
    private record Entry(String name, byte[] sha256, String userId) {
    }

    /** Who a matched key acts as. {@code userId} is a lowercase uuid. */
    public record Caller(String name, String userId) {
    }

    public static final IntegrationKeys EMPTY = new IntegrationKeys(List.of(), List.of());

    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final List<Entry> entries;
    private final List<String> rejected;

    private IntegrationKeys(List<Entry> entries, List<String> rejected) {
        this.entries = List.copyOf(entries);
        this.rejected = List.copyOf(rejected);
    }

    public static IntegrationKeys parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY;
        }
        List<Entry> ok = new ArrayList<>();
        List<String> bad = new ArrayList<>();
        String[] parts = raw.split(",");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                continue;
            }
            String[] f = part.split(":", -1);
            if (f.length != 3) {
                bad.add("entry " + (i + 1));
                continue;
            }
            String name = f[0].trim();
            String hash = f[1].trim().toLowerCase(Locale.ROOT);
            String userId = f[2].trim().toLowerCase(Locale.ROOT);
            if (!NAME.matcher(name).matches()
                    || !SHA256_HEX.matcher(hash).matches()
                    || !UUID_TEXT.matcher(userId).matches()) {
                bad.add("entry " + (i + 1));
                continue;
            }
            ok.add(new Entry(name, fromHex(hash), userId));
        }
        return new IntegrationKeys(ok, bad);
    }

    /** Lowercase hex SHA-256 of the UTF-8 bytes of {@code key}. */
    public static String sha256Hex(String key) {
        return Hex.bytesToHex(sha256(key));
    }

    public Optional<Caller> match(String presentedKey) {
        if (presentedKey == null || presentedKey.isEmpty() || entries.isEmpty()) {
            return Optional.empty();
        }
        byte[] presented = sha256(presentedKey);
        Entry found = null;
        for (Entry e : entries) {
            // No break: every entry is compared, whatever matched first.
            if (MessageDigest.isEqual(e.sha256(), presented) && found == null) {
                found = e;
            }
        }
        return found == null ? Optional.empty() : Optional.of(new Caller(found.name(), found.userId()));
    }

    public int size() {
        return entries.size();
    }

    /** Labels ({@code entry N}) of skipped malformed entries, for the startup log. */
    public List<String> rejected() {
        return rejected;
    }

    private static byte[] sha256(String key) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
        }
    }

    private static byte[] fromHex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(2 * i), 16);
            int lo = Character.digit(hex.charAt(2 * i + 1), 16);
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
