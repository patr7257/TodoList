package dk.dtu.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.OptionalInt;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signed session token, interchangeable with the website's {@code todo_session}
 * cookie value (website/src/lib/todo/auth.ts).
 *
 * <p>Format: {@code base64url(JSON {"uid":<userId>,"exp":<msEpoch>,"tv":<int>})
 * + "." + hex(HMAC-SHA256(base64urlPayload, TODO_SESSION_SECRET))}. TTL is 30
 * days.
 *
 * <p>The {@code tv} claim is the user's {@code users.token_version} at mint
 * time (issue #74), and it is appended LAST so {@code uid} and {@code exp} keep
 * the positions the pre-#74 format gave them. It is OPTIONAL on the way in: a
 * token carrying no {@code tv} was minted before this change and still
 * verifies, which is the only reason the column could be introduced without
 * signing everybody out. {@link AuthFilter} is what compares a present claim
 * against the stored value.
 *
 * <p>Cross-system verification does not depend on JSON key ordering: the
 * signature is computed over the base64url payload STRING, and verification
 * re-signs exactly the string it received. So a token minted here verifies on
 * the website and vice versa, as long as the secret matches.
 */
public final class Token {

    private static final long TTL_MILLIS = 30L * 24 * 60 * 60 * 1000;
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final String secret;

    public Token(String secret) {
        this.secret = secret;
    }

    /** True when a secret is configured, mirroring sessionConfigured(). */
    public boolean configured() {
        return secret != null && !secret.isBlank();
    }

    /**
     * A verified token: the user id it names, plus the {@code tv} claim it
     * carried, which is empty for a token minted before issue #74.
     *
     * <p>A record rather than a bare uid because the caller needs BOTH halves:
     * the uid to act as, and the claim to compare against the database. An
     * {@code Optional<String>} plus an out-parameter would have hidden the
     * second half from the type system, which is exactly where a transition
     * this quiet would go wrong unnoticed.
     */
    public record Session(String uid, OptionalInt tokenVersion) {
    }

    /**
     * Issues a token for the given user id, or null when no secret is
     * configured (matching createSessionCookieValue returning null).
     *
     * <p>This and the {@code (userId, expMillis)} overload mint the PRE-#74
     * shape, with no {@code tv} claim. They are kept, rather than defaulted to
     * version 0, because "no claim at all" is a real wire shape that has to
     * stay reproducible from the mint side: that is what pins the legacy test
     * vector in both directions instead of only on the verify side, where an
     * unknown-field-tolerant parser would happily stay green through a real
     * divergence.
     */
    public String issue(String userId) {
        return issue(userId, OptionalInt.empty());
    }

    /** Issues a token with an explicit expiry (milliseconds since epoch). */
    public String issue(String userId, long expMillis) {
        return issue(userId, expMillis, OptionalInt.empty());
    }

    /** Issues a token carrying (or deliberately omitting) a token version. */
    public String issue(String userId, OptionalInt tokenVersion) {
        return issue(userId, System.currentTimeMillis() + TTL_MILLIS, tokenVersion);
    }

    /**
     * Issues a token with an explicit expiry and token version.
     *
     * <p>The version is an {@link OptionalInt} rather than an {@code int} with
     * an int overload on purpose: {@code issue(uid, 7)} and
     * {@code issue(uid, 9999999999999L)} would otherwise differ only by the
     * literal's type, and Java would silently pick the int overload for the
     * first, turning an expiry into a token version. Spelling the absence out
     * costs a few characters and removes the trap.
     */
    public String issue(String userId, long expMillis, OptionalInt tokenVersion) {
        if (!configured()) {
            return null;
        }
        String json = "{\"uid\":\"" + escape(userId) + "\",\"exp\":" + expMillis
                + (tokenVersion.isPresent() ? ",\"tv\":" + tokenVersion.getAsInt() : "")
                + "}";
        String encoded = URL_ENCODER.encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + sign(encoded);
    }

    /**
     * Verifies a token and returns the {@link Session} it names, or empty when
     * the value is missing, malformed, tampered with, expired, or the secret is
     * unset.
     *
     * <p>Verification here stays purely cryptographic and touches no database.
     * Comparing the {@code tv} claim against {@code users.token_version} is the
     * caller's job ({@link AuthFilter}), which keeps this class testable
     * without a Postgres and keeps the website's mint side mirrorable one to
     * one.
     */
    public Optional<Session> verify(String value) {
        if (value == null || value.isEmpty() || !configured()) {
            return Optional.empty();
        }
        int dot = value.indexOf('.');
        if (dot <= 0 || dot == value.length() - 1 || value.indexOf('.', dot + 1) >= 0) {
            return Optional.empty();
        }
        String encoded = value.substring(0, dot);
        String signature = value.substring(dot + 1);

        byte[] expected = sign(encoded).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signature.getBytes(StandardCharsets.UTF_8);
        if (expected.length != actual.length || !MessageDigest.isEqual(expected, actual)) {
            return Optional.empty();
        }

        String json;
        try {
            json = new String(URL_DECODER.decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        String uid = extractString(json, "uid");
        Long exp = extractLong(json, "exp");
        if (uid == null || uid.isEmpty() || exp == null) {
            return Optional.empty();
        }
        if (System.currentTimeMillis() > exp) {
            return Optional.empty();
        }

        // Absent means "minted before issue #74", which is accepted. Present
        // but outside int range cannot equal any value the integer column can
        // hold, so it is treated as malformed rather than as a mismatch.
        Long tv = extractLong(json, "tv");
        OptionalInt tokenVersion = OptionalInt.empty();
        if (tv != null) {
            if (tv < Integer.MIN_VALUE || tv > Integer.MAX_VALUE) {
                return Optional.empty();
            }
            tokenVersion = OptionalInt.of(tv.intValue());
        }
        return Optional.of(new Session(uid, tokenVersion));
    }

    private String sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
            return Hex.bytesToHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    // Minimal, dependency-free extraction from the tiny fixed payload shape.
    private static String extractString(String json, String key) {
        String needle = "\"" + key + "\"";
        int k = json.indexOf(needle);
        if (k < 0) {
            return null;
        }
        int colon = json.indexOf(':', k + needle.length());
        if (colon < 0) {
            return null;
        }
        int start = json.indexOf('"', colon + 1);
        if (start < 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                sb.append(json.charAt(++i));
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return null;
    }

    private static Long extractLong(String json, String key) {
        String needle = "\"" + key + "\"";
        int k = json.indexOf(needle);
        if (k < 0) {
            return null;
        }
        int colon = json.indexOf(':', k + needle.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        int start = i;
        while (i < json.length() && (Character.isDigit(json.charAt(i)) || json.charAt(i) == '-')) {
            i++;
        }
        if (i == start) {
            return null;
        }
        try {
            return Long.parseLong(json.substring(start, i));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
