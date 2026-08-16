package dk.dtu.api.auth;

/**
 * Lowercase, zero-padded hex rendering of a byte array. Small, but part of the
 * session token WIRE FORMAT rather than a convenience.
 *
 * <p>A {@code todo_session} value ends in {@code hex(HMAC-SHA256(payload,
 * secret))}, and the website produces that half with Node's
 * {@code Buffer.toString("hex")}, which is always lowercase and always two
 * characters per byte. {@link Token#verify} compares the two signature STRINGS,
 * so any rendering difference here is not a cosmetic difference: it is a 401 on
 * every request, in a system where the Java API and the Next.js website mint
 * tokens for each other. {@code TokenTest} and the website's
 * {@code session.test.ts} pin the same hand-computed vector from their own
 * sides precisely because that is the only place the mismatch would surface.
 *
 * <p>Hence the explicit nibble loop, which avoids both JDK traps:
 * {@code Integer.toHexString(b)} sign-extends a negative byte, so {@code 0x80}
 * becomes {@code "ffffff80"} instead of {@code "80"}, and it drops the leading
 * zero on anything below {@code 0x10}. {@code String.format("%02x", b)} is
 * correct but builds a {@code Formatter} per byte, on a path that runs for
 * every authenticated request.
 *
 * <p>This lived as a package-private helper on {@code Scrypt} until password
 * login was retired (issue #61). It moved here first, on its own, because
 * {@code Token} depended on it: deleting {@code Scrypt} with the helper still
 * inside would have broken the build, and the token format must not be
 * anchored to a class whose reason to exist has been removed.
 */
public final class Hex {

    private Hex() {
    }

    /** Renders bytes as lowercase hex, two characters per byte, no separators. */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
