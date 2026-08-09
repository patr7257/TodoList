package dk.dtu.api.domain;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generation and shape validation for public share-link tokens (issue #52).
 *
 * <p>Deliberately dependency-free (JDK only, no JDBI, no Javalin) so the one
 * security-critical bit of this feature, "is the token unguessable and is it
 * the right shape", is trivially unit-testable without a database or a server.
 *
 * <p>A token is 24 bytes from {@link SecureRandom} in URL-safe base64 without
 * padding: exactly 32 characters from {@code [A-Za-z0-9_-]}, carrying 192 bits
 * of entropy. URL-safe matters because the token is pasted into a link path;
 * dropping the padding avoids a trailing {@code =} that some chat clients strip
 * off the end of an auto-linked URL.
 */
public final class ShareTokens {

    /** 24 random bytes encode to exactly 32 unpadded base64 characters. */
    public static final int TOKEN_LENGTH = 32;

    private static final int TOKEN_BYTES = 24;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private ShareTokens() {
    }

    /** A fresh, unguessable token: 192 bits of entropy, 32 URL-safe characters. */
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    /**
     * True when the value has the exact shape {@link #generate()} produces.
     *
     * <p>Used by the public controller as a cheap pre-database guard so that a
     * junk path segment (a crawler, a truncated paste) never reaches Postgres.
     * It is a shape check only, never an authorization check: a well-shaped but
     * unknown token still has to fail against the database, and both failures
     * answer with the same 404 so the two are indistinguishable from outside.
     */
    public static boolean isWellFormed(String token) {
        if (token == null || token.length() != TOKEN_LENGTH) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
