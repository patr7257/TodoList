package dk.dtu.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pins the hex rendering the session token signature is made of. These cases
 * used to be covered only indirectly, through the scrypt vector in
 * {@code ScryptTest} and the token vector in {@code TokenTest}; password login
 * is gone (issue #61) so the scrypt half went with it, and the byte-level rules
 * are asserted here directly instead of resting on one HMAC vector.
 */
class HexTest {

    @Test
    void rendersLowercaseTwoCharactersPerByte() {
        assertEquals("00", Hex.bytesToHex(new byte[] {0x00}));
        assertEquals("0f", Hex.bytesToHex(new byte[] {0x0f}));
        assertEquals("7f", Hex.bytesToHex(new byte[] {0x7f}));
        assertEquals("deadbeef", Hex.bytesToHex(new byte[] {
                (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef}));
    }

    @Test
    void doesNotSignExtendANegativeByte() {
        // Integer.toHexString would give "ffffff80" here, which would make every
        // signature comparison against the website's value fail.
        assertEquals("80", Hex.bytesToHex(new byte[] {(byte) 0x80}));
        assertEquals("ff", Hex.bytesToHex(new byte[] {(byte) 0xff}));
        assertEquals("8000ff", Hex.bytesToHex(new byte[] {(byte) 0x80, 0x00, (byte) 0xff}));
    }

    @Test
    void emptyInputRendersAsAnEmptyString() {
        assertEquals("", Hex.bytesToHex(new byte[0]));
    }

    @Test
    void producesTwoCharactersForEveryPossibleByteValue() {
        byte[] all = new byte[256];
        for (int i = 0; i < 256; i++) {
            all[i] = (byte) i;
        }
        String hex = Hex.bytesToHex(all);
        assertEquals(512, hex.length(), "every byte must render as exactly two characters");
        assertEquals(hex.toLowerCase(), hex, "the rendering must be lowercase throughout");
    }
}
