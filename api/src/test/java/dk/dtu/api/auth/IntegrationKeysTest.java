package dk.dtu.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import dk.dtu.api.ApiConfig;

import org.junit.jupiter.api.Test;

/**
 * Pins how TODO_INTEGRATION_KEYS is read: only SHA-256 hashes are configured,
 * a presented key is hashed and compared, and a malformed entry is skipped and
 * reported by position only, never by content.
 */
class IntegrationKeysTest {

    /** FIPS 180-2 test vector: SHA-256("abc"). */
    private static final String ABC_SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final String USER = "6f1c2a7e-3b4d-4c5e-8f90-a1b2c3d4e5f6";
    private static final String USER2 = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";

    @Test
    void sha256HexMatchesTheFipsVector() {
        assertEquals(ABC_SHA256, IntegrationKeys.sha256Hex("abc"));
    }

    @Test
    void parsesOneEntryAndMatchesItsKey() {
        IntegrationKeys keys = IntegrationKeys.parse("bartender:" + ABC_SHA256 + ":" + USER);
        assertEquals(1, keys.size());
        IntegrationKeys.Caller caller = keys.match("abc").orElseThrow();
        assertEquals("bartender", caller.name());
        assertEquals(USER, caller.userId());
    }

    @Test
    void aWrongEmptyOrMissingKeyDoesNotMatch() {
        IntegrationKeys keys = IntegrationKeys.parse("bartender:" + ABC_SHA256 + ":" + USER);
        assertTrue(keys.match("abd").isEmpty());
        assertTrue(keys.match("").isEmpty());
        assertTrue(keys.match(null).isEmpty());
    }

    @Test
    void parsesSeveralEntriesSeparatedByCommasWithSurroundingSpaces() {
        String raw = "a:" + IntegrationKeys.sha256Hex("k1") + ":" + USER
                + " , b:" + IntegrationKeys.sha256Hex("k2") + ":" + USER2;
        IntegrationKeys keys = IntegrationKeys.parse(raw);
        assertEquals(2, keys.size());
        assertEquals("b", keys.match("k2").orElseThrow().name());
        assertEquals(USER2, keys.match("k2").orElseThrow().userId());
    }

    @Test
    void uppercaseHashAndUserIdAreNormalised() {
        IntegrationKeys keys = IntegrationKeys.parse(
                "bartender:" + ABC_SHA256.toUpperCase() + ":" + USER.toUpperCase());
        assertEquals(USER, keys.match("abc").orElseThrow().userId());
    }

    @Test
    void malformedEntriesAreSkippedAndReportedByPositionOnly() {
        String raw = "bartender:" + ABC_SHA256 + ":" + USER
                + ",broken"
                + ",x:nothex:" + USER
                + ",Bad Name:" + ABC_SHA256 + ":" + USER
                + ",y:" + ABC_SHA256 + ":not-a-uuid"
                + ",a:b:c:d";
        IntegrationKeys keys = IntegrationKeys.parse(raw);
        assertEquals(1, keys.size());
        assertEquals(List.of("entry 2", "entry 3", "entry 4", "entry 5", "entry 6"), keys.rejected());
    }

    @Test
    void nullOrBlankConfiguresNothing() {
        assertEquals(0, IntegrationKeys.parse(null).size());
        assertEquals(0, IntegrationKeys.parse("   ").size());
        assertTrue(IntegrationKeys.parse("").match("abc").isEmpty());
    }

    @Test
    void apiConfigCarriesParsedKeysAndDefaultsToNone() {
        ApiConfig plain = ApiConfig.of(0, null, "s");
        assertEquals(0, plain.integrationKeys().size());
        ApiConfig withKeys = plain.withIntegrationKeys("bartender:" + ABC_SHA256 + ":" + USER);
        assertTrue(withKeys.integrationKeys().match("abc").isPresent());
        assertEquals(plain.sessionSecret(), withKeys.sessionSecret());
    }
}
