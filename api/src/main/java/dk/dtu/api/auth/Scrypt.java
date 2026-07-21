package dk.dtu.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import org.bouncycastle.crypto.generators.SCrypt;

/**
 * scrypt password hashing that is byte-for-byte compatible with the website's
 * Node implementation in website/src/lib/project-gate.ts.
 *
 * <p>Stored format is {@code "<saltHex>:<hashHex>"}. The website produces this
 * with Node's {@code crypto.scryptSync(password, salt, keylen)} using the
 * default parameters (N=16384, r=8, p=1) and keylen=64.
 *
 * <p>CRITICAL COMPATIBILITY DETAIL: Node's scryptSync, when given the salt as a
 * String, feeds the STRING's raw UTF-8 bytes to scrypt. Here the stored salt is
 * a hex string, and Node does NOT hex-decode it before hashing: it hashes the
 * ASCII/UTF-8 bytes of the hex string itself. So the verify below uses the salt
 * segment's UTF-8 bytes verbatim, never Hex.decode(salt).
 */
public final class Scrypt {

    private static final int COST_N = 16384;
    private static final int BLOCK_SIZE_R = 8;
    private static final int PARALLELISM_P = 1;
    private static final int DEFAULT_KEYLEN = 64;
    private static final int SALT_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    private Scrypt() {
    }

    /**
     * Verifies a plaintext password against a stored {@code saltHex:hashHex}
     * value using a constant-time comparison. Returns false on any malformed
     * input rather than throwing.
     */
    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        int colon = stored.indexOf(':');
        if (colon <= 0 || colon == stored.length() - 1) {
            return false;
        }
        String saltHex = stored.substring(0, colon);
        String hashHex = stored.substring(colon + 1);

        byte[] expected;
        try {
            expected = hexToBytes(hashHex);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (expected.length == 0) {
            return false;
        }

        // Salt = the raw UTF-8 bytes of the hex salt STRING (not hex-decoded),
        // matching Node scryptSync(password, saltString, keylen). keylen matches
        // the stored hash length, exactly like the website's verifyPassword.
        byte[] actual = SCrypt.generate(
                password.getBytes(StandardCharsets.UTF_8),
                saltHex.getBytes(StandardCharsets.UTF_8),
                COST_N, BLOCK_SIZE_R, PARALLELISM_P, expected.length);

        return MessageDigest.isEqual(actual, expected);
    }

    /**
     * Produces a fresh {@code saltHex:hashHex} value in the exact format the
     * website stores, so a seeder here and the website's login agree. Uses a
     * 16-byte random salt rendered as a 32-char hex string, then hashes the
     * UTF-8 bytes of that hex string (matching Node's default behaviour).
     */
    public static String hash(String password) {
        byte[] saltRaw = new byte[SALT_BYTES];
        RANDOM.nextBytes(saltRaw);
        String saltHex = bytesToHex(saltRaw);
        byte[] hash = SCrypt.generate(
                password.getBytes(StandardCharsets.UTF_8),
                saltHex.getBytes(StandardCharsets.UTF_8),
                COST_N, BLOCK_SIZE_R, PARALLELISM_P, DEFAULT_KEYLEN);
        return saltHex + ":" + bytesToHex(hash);
    }

    static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    static byte[] hexToBytes(String hex) {
        int len = hex.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("odd-length hex");
        }
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("invalid hex");
            }
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
