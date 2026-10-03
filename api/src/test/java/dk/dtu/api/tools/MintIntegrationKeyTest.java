package dk.dtu.api.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;

import dk.dtu.api.auth.IntegrationKeys;

import org.junit.jupiter.api.Test;

class MintIntegrationKeyTest {

    private static final String USER = "6f1c2a7e-3b4d-4c5e-8f90-a1b2c3d4e5f6";

    @Test
    void newKeyIs256BitsOfUrlSafeBase64WithoutPadding() {
        String key = MintIntegrationKey.newKey(new SecureRandom());
        assertEquals(43, key.length());
        assertTrue(key.matches("[A-Za-z0-9_-]{43}"));
        assertNotEquals(key, MintIntegrationKey.newKey(new SecureRandom()));
    }

    @Test
    void envEntryIsNameHashAndUserAndMatchesTheKey() {
        String entry = MintIntegrationKey.envEntry("bartender", "abc", USER);
        assertEquals("bartender:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad:" + USER, entry);
        assertEquals(USER, IntegrationKeys.parse(entry).match("abc").orElseThrow().userId());
    }

    @Test
    void envEntryTrimsAndLowercasesTheUserId() {
        String entry = MintIntegrationKey.envEntry(" bartender ", "abc", " " + USER.toUpperCase() + " ");
        assertTrue(entry.endsWith(":" + USER));
        assertTrue(entry.startsWith("bartender:"));
    }

    @Test
    void envEntryRefusesABadNameOrUserId() {
        assertThrows(IllegalArgumentException.class, () -> MintIntegrationKey.envEntry("Bar Tender", "abc", USER));
        assertThrows(IllegalArgumentException.class, () -> MintIntegrationKey.envEntry("", "abc", USER));
        assertThrows(IllegalArgumentException.class, () -> MintIntegrationKey.envEntry("bartender", "abc", "not-a-uuid"));
    }
}
