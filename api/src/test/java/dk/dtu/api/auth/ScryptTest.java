package dk.dtu.api.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScryptTest {

    // Generated with Node: crypto.scryptSync("correct horse battery staple",
    // "0123456789abcdef0123456789abcdef", 64) using the default params
    // (N=16384, r=8, p=1). The salt is the 32-char hex STRING, and Node hashes
    // that string's UTF-8 bytes (it does NOT hex-decode the salt). This vector
    // is the whole point of the compatibility: if our verify hex-decoded the
    // salt, it would fail.
    private static final String NODE_PASSWORD = "correct horse battery staple";
    private static final String NODE_STORED =
            "0123456789abcdef0123456789abcdef:"
            + "b9e4c0458defb164feba9d9ffbec86e4ddffb2a590e4afa11b8a170fbd151218"
            + "0c3a5eefbb59f1d53515369c1639cc3cf7f291109291b9082c04aebd75e4c8cb";

    @Test
    void verifiesAgainstAKnownNodeScryptVector() {
        assertTrue(Scrypt.verify(NODE_PASSWORD, NODE_STORED),
                "must verify a hash produced by Node's scryptSync with a hex-string salt");
    }

    @Test
    void rejectsWrongPassword() {
        assertFalse(Scrypt.verify("wrong password", NODE_STORED));
    }

    @Test
    void rejectsMalformedStoredValue() {
        assertFalse(Scrypt.verify("x", "no-colon-here"));
        assertFalse(Scrypt.verify("x", "salt:"));
        assertFalse(Scrypt.verify("x", ":hash"));
        assertFalse(Scrypt.verify("x", "salt:zzzz"));
        assertFalse(Scrypt.verify(null, NODE_STORED));
        assertFalse(Scrypt.verify("x", null));
    }

    @Test
    void hashThenVerifyRoundTrips() {
        String stored = Scrypt.hash("hunter2");
        assertTrue(stored.contains(":"));
        assertTrue(Scrypt.verify("hunter2", stored));
        assertFalse(Scrypt.verify("hunter3", stored));
    }
}
