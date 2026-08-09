package dk.dtu.auth;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the PKCE derivation and the fallback-code normalization for the
 * browser-mediated desktop sign in (issue #51).
 *
 * <p>The fixed vector below is RFC 7636 Appendix B, so both halves of the
 * handshake can be checked against the same published numbers instead of
 * against each other. The website half,
 * {@code website/src/lib/todo/desktop-handoff.test.ts} in the
 * {@code patr7257/PatrickRobelWeb} repo, MUST assert this exact pair: if the two
 * sides ever disagree about what is hashed (the verifier STRING, not the random
 * bytes it encodes) every sign in fails with a challenge mismatch, and nothing
 * else in either test suite would catch it.
 */
public class PkceTest {

    // RFC 7636 Appendix B, the canonical S256 example.
    private static final String FIXED_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String FIXED_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @Test
    void challengeMatchesTheFixedVector() {
        assertEquals(FIXED_CHALLENGE, Pkce.challengeFor(FIXED_VERIFIER));
    }

    @Test
    void challengeHashesTheVerifierStringNotItsDecodedBytes() throws Exception {
        // The one derivation mistake that still produces a plausible looking
        // 43 character challenge: hashing the 32 random bytes instead of the
        // ASCII of the verifier. Pinned so it cannot creep back in.
        byte[] decoded = java.util.Base64.getUrlDecoder().decode(FIXED_VERIFIER);
        String wrong = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                java.security.MessageDigest.getInstance("SHA-256").digest(decoded));
        assertNotEquals(wrong, Pkce.challengeFor(FIXED_VERIFIER));
    }

    @Test
    void challengeIsUnpaddedBase64UrlOf43Characters() {
        String challenge = Pkce.challengeFor("anything at all");
        assertEquals(43, challenge.length());
        assertTrue(challenge.matches("[A-Za-z0-9_-]+"), challenge);
    }

    @Test
    void stateAndVerifierAreUnpaddedBase64UrlOf43Characters() {
        Pkce.Handshake handshake = Pkce.newHandshake();

        for (String value : new String[] { handshake.state(), handshake.verifier() }) {
            assertEquals(43, value.length(), value);
            assertFalse(value.contains("="), "base64url must be unpadded: " + value);
            assertTrue(value.matches("[A-Za-z0-9_-]+"), value);
        }
        assertNotEquals(handshake.state(), handshake.verifier());
        assertEquals(Pkce.challengeFor(handshake.verifier()), handshake.challenge());
    }

    @Test
    void everyHandshakeIsDistinct() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            Pkce.Handshake handshake = Pkce.newHandshake();
            assertTrue(seen.add(handshake.state()), "state repeated");
            assertTrue(seen.add(handshake.verifier()), "verifier repeated");
        }
    }

    @Test
    void normalizeCodeUppercasesAndStripsDashesAndWhitespace() {
        assertEquals("ABCD2345", Pkce.normalizeCode("abcd-2345"));
        assertEquals("ABCD2345", Pkce.normalizeCode("ABCD-2345"));
        assertEquals("ABCD2345", Pkce.normalizeCode("  abcd 2345  "));
        assertEquals("ABCD2345", Pkce.normalizeCode("aB-cD 23\t45\n"));
        assertEquals("ABCD2345", Pkce.normalizeCode("abcd2345"));
    }

    @Test
    void normalizeCodeHandlesNullAndEmpty() {
        assertEquals("", Pkce.normalizeCode(null));
        assertEquals("", Pkce.normalizeCode(""));
        assertEquals("", Pkce.normalizeCode("   -  "));
    }

    @Test
    void looksLikeCodeAcceptsTheAlphabetAndRejectsTheAmbiguousCharacters() {
        assertTrue(Pkce.looksLikeCode("ABCD2345"));
        assertTrue(Pkce.looksLikeCode(Pkce.normalizeCode("hjkm-npqr")));

        assertFalse(Pkce.looksLikeCode(""), "empty is not a code");
        assertFalse(Pkce.looksLikeCode(null));
        // I, L, O, 0 and 1 are deliberately absent from the alphabet.
        assertFalse(Pkce.looksLikeCode("ABCDI234"));
        assertFalse(Pkce.looksLikeCode("ABCD0234"));
        assertFalse(Pkce.looksLikeCode("ABCD-234"), "normalize before checking");
    }
}
