package dk.dtu.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Pins the refill prompt (issue #59) as TEXT, without a server.
 *
 * <p>The prompt is a deliverable, not a debug string: it is pasted into a Claude
 * session and whatever it says is what comes back. So the four things it has to
 * name (the deck, its metadata keys, how many cards, the exact endpoint) are
 * asserted individually, and so is the house rule about pasted material, which
 * a plain integration test would never notice being broken.
 */
class TinderPromptsTest {

    private static final String BASE = "https://api.todolist.patrickrobel.dk";

    @Test
    void namesTheDeckTheKeysTheCountAndTheExactEndpoint() {
        String prompt = TinderPrompts.refill("aktiviteter", "AcTindervitivities",
                List.of("kategori", "sted", "varighed"), 50, BASE);

        assertTrue(prompt.contains("AcTindervitivities"), prompt);
        assertTrue(prompt.contains("Deck key: aktiviteter"), prompt);
        assertTrue(prompt.contains("Cards to generate: 50"), prompt);
        assertTrue(prompt.contains("kategori, sted, varighed"), prompt);
        assertTrue(prompt.contains(BASE + "/api/todo/tinder/decks/aktiviteter/entries"), prompt);
    }

    @Test
    void anEmptyDeckSaysSoInsteadOfInventingAMetadataVocabulary() {
        String prompt = TinderPrompts.refill("nyt", "Nyt dæk", List.of(), 50, BASE);
        assertTrue(prompt.contains("none yet"), prompt);
        assertTrue(prompt.contains("empty metadata object"), prompt);
    }

    @Test
    void carriesNoPlaceholderForAnyoneToFillIn() {
        // House rule: anything handed over to be pasted must work verbatim.
        // An angle-bracket placeholder in a line meant for a shell is a syntax
        // error, and a fill-in token is exactly the kind of edit that trips a
        // paste up. The prompt therefore contains neither, and above all it does
        // not ask for the session token: the committed script collects that.
        String prompt = TinderPrompts.refill("aktiviteter", "AcTindervitivities",
                List.of("kategori"), 50, BASE);

        assertFalse(prompt.contains("<"), "no angle-bracket placeholders: " + prompt);
        assertFalse(prompt.contains(">"), "no angle-bracket placeholders: " + prompt);
        assertFalse(prompt.toUpperCase().contains("YOUR_"), "no fill-in tokens: " + prompt);
        assertFalse(prompt.toUpperCase().contains("REPLACE"), "no fill-in tokens: " + prompt);
        assertTrue(prompt.contains("scripts\\tinder-refill.ps1"),
                "the one runnable line must be the committed script that prompts for the token");
        assertTrue(prompt.contains("Do not ask Patrick for the API token"), prompt);
    }

    @Test
    void importUrlToleratesAMissingBaseWithoutProducingNullInTheUrl() {
        assertEquals("/api/todo/tinder/decks/x/entries", TinderPrompts.importUrl(null, "x"));
        assertEquals(BASE + "/api/todo/tinder/decks/x/entries", TinderPrompts.importUrl(BASE, "x"));
    }

    @Test
    void onlyADrainedDepleteDeckAsksForARefill() {
        // Above the threshold: nothing to do.
        assertFalse(TinderPrompts.needsRefill(false, TinderPrompts.REFILL_THRESHOLD + 1));
        // At and below it: ask, while there are still a few cards left to swipe.
        assertTrue(TinderPrompts.needsRefill(false, TinderPrompts.REFILL_THRESHOLD));
        assertTrue(TinderPrompts.needsRefill(false, 0));
        // The grocery deck recycles forever, so it can never be drained and must
        // never nag: its cards are staples, not generated ideas.
        assertFalse(TinderPrompts.needsRefill(true, 0));
    }
}
