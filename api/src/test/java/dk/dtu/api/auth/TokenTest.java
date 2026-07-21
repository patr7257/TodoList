package dk.dtu.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class TokenTest {

    private static final String SECRET = "test-secret";

    // Generated with Node from website/src/lib/todo/auth.ts's scheme:
    // base64url(JSON {"uid":"user-123","exp":9999999999999}) + "." +
    // hex(HMAC-SHA256(payload, "test-secret")).
    private static final String NODE_TOKEN =
            "eyJ1aWQiOiJ1c2VyLTEyMyIsImV4cCI6OTk5OTk5OTk5OTk5OX0"
            + ".4691801ccd1e28258cd1b6c4138007f78ba82a37b951c26d7d4f5b35048912ac";

    @Test
    void verifiesAWebsiteIssuedToken() {
        Optional<String> uid = new Token(SECRET).verify(NODE_TOKEN);
        assertTrue(uid.isPresent());
        assertEquals("user-123", uid.get());
    }

    @Test
    void issuesTheSameStringAsTheWebsiteForTheSamePayload() {
        // Key order matches JSON.stringify({uid, exp}), so the encoded payload
        // and signature are byte-identical to Node's, proving interchangeability.
        String issued = new Token(SECRET).issue("user-123", 9999999999999L);
        assertEquals(NODE_TOKEN, issued);
    }

    @Test
    void issueThenVerifyRoundTrips() {
        Token token = new Token(SECRET);
        String value = token.issue("abc-def");
        assertEquals("abc-def", token.verify(value).orElse(null));
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
    void rejectsExpiredToken() {
        Token token = new Token(SECRET);
        String expired = token.issue("abc-def", System.currentTimeMillis() - 1000);
        assertTrue(token.verify(expired).isEmpty());
    }

    @Test
    void rejectsWrongSecret() {
        assertTrue(new Token("different-secret").verify(NODE_TOKEN).isEmpty());
    }

    @Test
    void unconfiguredSecretIssuesNullAndVerifiesNothing() {
        Token token = new Token(null);
        assertFalse(token.configured());
        assertEquals(null, token.issue("x"));
        assertTrue(token.verify(NODE_TOKEN).isEmpty());
    }
}
