package dk.dtu.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

class TokenTest {

    private static final String SECRET = "test-secret";

    // Generated with Node from website/src/lib/todo/auth.ts's scheme:
    // base64url(JSON {"uid":"user-123","exp":9999999999999}) + "." +
    // hex(HMAC-SHA256(payload, "test-secret")).
    private static final String NODE_TOKEN =
            "eyJ1aWQiOiJ1c2VyLTEyMyIsImV4cCI6OTk5OTk5OTk5OTk5OX0"
            + ".4691801ccd1e28258cd1b6c4138007f78ba82a37b951c26d7d4f5b35048912ac";

    // The same scheme with the issue #74 "tv" claim appended LAST:
    // base64url(JSON {"uid":"user-123","exp":9999999999999,"tv":7}) + "." + hex(HMAC...).
    private static final String NODE_TOKEN_TV7 =
            "eyJ1aWQiOiJ1c2VyLTEyMyIsImV4cCI6OTk5OTk5OTk5OTk5OSwidHYiOjd9"
            + ".43a2766c06f24c865ddc2deac40510898bdfa1c1598bb3acd8293098f06b9a6b";

    @Test
    void verifiesAWebsiteIssuedToken() {
        Optional<Token.Session> session = new Token(SECRET).verify(NODE_TOKEN);
        assertTrue(session.isPresent());
        assertEquals("user-123", session.get().uid());
    }

    @Test
    void aLegacyTokenCarriesNoVersionAndStillVerifies() {
        // Every session minted before issue #74 looks like this. If this ever
        // goes red, the next deploy signs every live session out.
        Token.Session session = new Token(SECRET).verify(NODE_TOKEN).orElseThrow();
        assertTrue(session.tokenVersion().isEmpty());
    }

    @Test
    void verifiesAVersionedWebsiteIssuedToken() {
        Token.Session session = new Token(SECRET).verify(NODE_TOKEN_TV7).orElseThrow();
        assertEquals("user-123", session.uid());
        assertEquals(OptionalInt.of(7), session.tokenVersion());
    }

    @Test
    void issuesTheSameStringAsTheWebsiteForTheSamePayload() {
        // Key order matches JSON.stringify({uid, exp}), so the encoded payload
        // and signature are byte-identical to Node's, proving interchangeability.
        String issued = new Token(SECRET).issue("user-123", 9999999999999L);
        assertEquals(NODE_TOKEN, issued);
    }

    @Test
    void issuesTheSameStringAsTheWebsiteForAVersionedPayload() {
        // THIS is the assertion that catches real drift between the two
        // implementations. The verify side tolerates unknown fields by design
        // (it scans the payload by key name), so a verify-only test stays green
        // even when Java and Node have stopped producing the same bytes. Only
        // the mint side pins the JSON construction: key order, the claim name,
        // no spaces, and the claim appended LAST.
        String issued = new Token(SECRET).issue("user-123", 9999999999999L, OptionalInt.of(7));
        assertEquals(NODE_TOKEN_TV7, issued);
    }

    @Test
    void issueThenVerifyRoundTrips() {
        Token token = new Token(SECRET);
        String value = token.issue("abc-def");
        assertEquals("abc-def", token.verify(value).map(Token.Session::uid).orElse(null));
    }

    @Test
    void issueThenVerifyRoundTripsAVersion() {
        Token token = new Token(SECRET);
        String value = token.issue("abc-def", OptionalInt.of(3));
        Token.Session session = token.verify(value).orElseThrow();
        assertEquals("abc-def", session.uid());
        assertEquals(OptionalInt.of(3), session.tokenVersion());
    }

    @Test
    void versionZeroIsCarriedAsAClaimAndNotAsAnAbsence() {
        // Zero is the default every user starts at, so "0" and "no claim" must
        // stay distinguishable: only the latter is a pre-#74 token.
        Token token = new Token(SECRET);
        Token.Session session = token.verify(token.issue("abc-def", OptionalInt.of(0))).orElseThrow();
        assertEquals(OptionalInt.of(0), session.tokenVersion());
    }

    @Test
    void rejectsTamperedSignature() {
        Token token = new Token(SECRET);
        String value = token.issue("abc-def");
        String tampered = value.substring(0, value.length() - 1)
                + (value.endsWith("a") ? "b" : "a");
        assertTrue(token.verify(tampered).isEmpty());
    }

    @Test
    void rejectsTamperedPayload() {
        Token token = new Token(SECRET);
        String value = token.issue("abc-def");
        int dot = value.indexOf('.');
        String tampered = "eyJ1aWQiOiJoYWNrZXIiLCJleHAiOjk5OTk5OTk5OTk5OTl9" + value.substring(dot);
        assertTrue(token.verify(tampered).isEmpty());
    }

    @Test
    void rejectsAStrippedVersionClaim() {
        // The signature covers the encoded payload STRING, so a versioned token
        // cannot be downgraded to a claimless one to dodge a revocation.
        Token token = new Token(SECRET);
        String versioned = token.issue("abc-def", 9999999999999L, OptionalInt.of(4));
        String legacy = token.issue("abc-def", 9999999999999L);
        String downgraded = legacy.substring(0, legacy.indexOf('.'))
                + versioned.substring(versioned.indexOf('.'));
        assertTrue(token.verify(downgraded).isEmpty());
    }

    @Test
    void rejectsExpiredToken() {
        Token token = new Token(SECRET);
        String expired = token.issue("abc-def", System.currentTimeMillis() - 1000);
        assertTrue(token.verify(expired).isEmpty());
    }

    @Test
    void rejectsWrongSecret() {
        assertTrue(new Token("different-secret").verify(NODE_TOKEN).isEmpty());
        assertTrue(new Token("different-secret").verify(NODE_TOKEN_TV7).isEmpty());
    }

    @Test
    void unconfiguredSecretIssuesNullAndVerifiesNothing() {
        Token token = new Token(null);
        assertFalse(token.configured());
        assertEquals(null, token.issue("x"));
        assertEquals(null, token.issue("x", OptionalInt.of(1)));
        assertTrue(token.verify(NODE_TOKEN).isEmpty());
    }
}
