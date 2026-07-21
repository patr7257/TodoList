package dk.dtu.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signed session token, interchangeable with the website's {@code todo_session}
 * cookie value (website/src/lib/todo/auth.ts).
 *
 * <p>Format: {@code base64url(JSON {"uid":<userId>,"exp":<msEpoch>}) + "." +
 * hex(HMAC-SHA256(base64urlPayload, TODO_SESSION_SECRET))}. TTL is 30 days.
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
     * Issues a token for the given user id, or null when no secret is
     * configured (matching createSessionCookieValue returning null).
     */
    public String issue(String userId) {
        return issue(userId, System.currentTimeMillis() + TTL_MILLIS);
    }

    /** Issues a token with an explicit expiry (milliseconds since epoch). */
    public String issue(String userId, long expMillis) {
        if (!configured()) {
            return null;
        }
        String json = "{\"uid\":\"" + escape(userId) + "\",\"exp\":" + expMillis + "}";
        String encoded = URL_ENCODER.encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + sign(encoded);
    }

    /**
     * Verifies a token and returns the user id, or empty when the value is
     * missing, malformed, tampered with, expired, or the secret is unset.
     */
    public Optional<String> verify(String value) {
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
        return Optional.of(uid);
    }

    private String sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
            return Scrypt.bytesToHex(raw);
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
