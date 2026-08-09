package dk.dtu.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

/**
 * PKCE values for the browser-mediated desktop sign in (issue #51), plus the
 * normalization rule for the typeable fallback code.
 *
 * <p>Pure: no JavaFX, no network, no I/O. The derivation is standard PKCE S256
 * (RFC 7636) and is pinned byte for byte against the website half of the
 * handshake, which lives in {@code website/src/lib/todo/desktop-handoff.ts} in
 * the {@code patr7257/PatrickRobelWeb} repo:
 *
 * <ul>
 *   <li>{@code state} and {@code verifier} are base64url of 32 random bytes
 *       from {@link SecureRandom}, with NO padding (43 characters).</li>
 *   <li>{@code challenge} is base64url, no padding, of the SHA-256 of the UTF-8
 *       bytes of the verifier STRING. The ASCII of the verifier is hashed, never
 *       the random bytes it was encoded from.</li>
 * </ul>
 */
public final class Pkce {

    /**
     * The alphabet the website draws the typeable fallback code from: no I, L,
     * O, 0 or 1, so nothing is ambiguous when read off a phone screen.
     */
    public static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    private Pkce() {
    }

    /** One sign-in attempt's values: the anti-forgery state plus the PKCE pair. */
    public record Handshake(String state, String verifier, String challenge) {
    }

    /** A fresh state + verifier + derived challenge for one sign-in attempt. */
    public static Handshake newHandshake() {
        String state = randomValue();
        String verifier = randomValue();
        return new Handshake(state, verifier, challengeFor(verifier));
    }

    /** base64url (no padding) of 32 fresh random bytes, i.e. 43 characters. */
    public static String randomValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return BASE64URL.encodeToString(bytes);
    }

    /**
     * Standard PKCE S256 challenge: base64url (no padding) of the SHA-256 of the
     * verifier string's UTF-8 bytes.
     */
    public static String challengeFor(String verifier) {
        if (verifier == null) {
            throw new IllegalArgumentException("verifier must not be null");
        }
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(verifier.getBytes(StandardCharsets.UTF_8));
            return BASE64URL.encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory on every Java platform, so this cannot happen.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /**
     * Normalizes a hand-typed fallback code before it is sent: uppercase, with
     * every dash and every kind of whitespace removed. The website displays the
     * code grouped as {@code XXXX-XXXX}, so the dash is the one character users
     * will type that the server never stored. Null becomes the empty string.
     */
    public static String normalizeCode(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '-' || Character.isWhitespace(c)) {
                continue;
            }
            out.append(c);
        }
        return out.toString().toUpperCase(Locale.ROOT);
    }

    /**
     * True when an already-normalized code contains only alphabet characters and
     * is not empty. Deliberately length-agnostic: it exists to catch an obvious
     * typo before a network round trip, not to re-implement the server's
     * validation (which owns length, expiry and single use).
     */
    public static boolean looksLikeCode(String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return false;
        }
        for (int i = 0; i < normalized.length(); i++) {
            if (CODE_ALPHABET.indexOf(normalized.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }
}
