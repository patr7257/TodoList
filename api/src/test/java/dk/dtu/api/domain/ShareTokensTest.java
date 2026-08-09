package dk.dtu.api.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Pins the share token's shape and uniqueness. This is the one security
 * primitive of issue #52: a token that is short, predictable, or contains
 * characters that get mangled in a URL is a broken share link.
 */
class ShareTokensTest {

    private static final Pattern SHAPE = Pattern.compile("^[A-Za-z0-9_-]{32}$");

    @Test
    void twoHundredTokensAreAllDistinctAndAllWellShaped() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String token = ShareTokens.generate();
            assertEquals(32, token.length(), "expected exactly 32 characters, got: " + token);
            assertTrue(SHAPE.matcher(token).matches(),
                    "token must match ^[A-Za-z0-9_-]{32}$, got: " + token);
            assertTrue(seen.add(token), "generated a duplicate token: " + token);
        }
        assertEquals(200, seen.size());
    }

    @Test
    void tokensNeverContainStandardBase64CharactersThatBreakInAUrl() {
        // '+' and '/' are path/query hostile and '=' padding gets stripped by
        // chat clients auto-linking a URL, which is why the encoder is the
        // URL-safe one without padding.
        for (int i = 0; i < 200; i++) {
            String token = ShareTokens.generate();
            assertFalse(token.contains("+"), "token must not contain '+': " + token);
            assertFalse(token.contains("/"), "token must not contain '/': " + token);
            assertFalse(token.contains("="), "token must not contain '=': " + token);
        }
    }

    @Test
    void generatedTokensAreAcceptedByTheShapeGuard() {
        for (int i = 0; i < 50; i++) {
            assertTrue(ShareTokens.isWellFormed(ShareTokens.generate()));
        }
    }

    @Test
    void shapeGuardRejectsNullWrongLengthAndForeignCharacters() {
        assertFalse(ShareTokens.isWellFormed(null));
        assertFalse(ShareTokens.isWellFormed(""));
        assertFalse(ShareTokens.isWellFormed("a".repeat(31)), "too short");
        assertFalse(ShareTokens.isWellFormed("a".repeat(33)), "too long");
        assertFalse(ShareTokens.isWellFormed("a".repeat(31) + "="), "padding is not part of the alphabet");
        assertFalse(ShareTokens.isWellFormed("a".repeat(31) + "/"), "slash is not part of the alphabet");
        assertFalse(ShareTokens.isWellFormed("a".repeat(31) + "+"), "plus is not part of the alphabet");
        assertFalse(ShareTokens.isWellFormed("a".repeat(31) + " "), "space is not part of the alphabet");
        assertFalse(ShareTokens.isWellFormed("a".repeat(31) + "å"), "non-ascii is not part of the alphabet");
        // A correctly shaped but never-issued token is still "well formed":
        // the guard is a shape check, never an authorization check.
        assertTrue(ShareTokens.isWellFormed("_-".repeat(16)));
    }
}
