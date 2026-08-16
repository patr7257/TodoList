package dk.dtu.api.web;

import java.util.List;

/**
 * Composes the ready-made refill prompt a drained deck carries (issue #59).
 *
 * <p>The refill mechanism is deliberately NOT a server-side LLM call. A deck
 * runs dry, the API hands back the exact text to paste into a Claude session,
 * that session generates cards and they come back through the authenticated
 * import endpoint. Zero running cost, no model API key in Dokploy, and review
 * is inherent because a person runs it. The import endpoint's contract is
 * identical either way, so switching to an automatic caller later is a switch
 * rather than a rewrite.
 *
 * <p>Pure and static on purpose: no database, no config lookup, no clock.
 * Everything it needs is a parameter, so {@code TinderPromptsTest} can pin the
 * exact wording without standing up a server, and so the composition can be
 * reviewed as text rather than inferred from an integration test.
 *
 * <p><b>House rule, and the reason this class is not just string concatenation
 * at the call site:</b> anything handed over to be pasted has to work verbatim,
 * with nothing to edit first. So the prompt carries no angle-bracket
 * placeholders and no fill-in tokens, and above all it does not ask the Claude
 * session to handle the API token. The one runnable line it hands over is the
 * committed helper script, which prompts for the token itself. A prompt that
 * said "POST it with your token" would be a prompt that leaks a session secret
 * into a chat transcript.
 */
public final class TinderPrompts {

    /**
     * How many cards one refill asks for. A deck is refilled a few times a year
     * at most, so this is a flat batch rather than something derived from the
     * deck's original size: the launch sizes (200 / 70 / 70 / 100) are a
     * property of the seed datasets in issue #57 and are not recorded in the
     * schema, and inventing a column to hold them would be a lot of machinery
     * for a number nobody will tune.
     */
    public static final int DEFAULT_REFILL_COUNT = 50;

    /**
     * When a deplete deck's remaining count drops to this or below, the deck
     * status starts carrying a refill prompt. Deliberately above zero: an
     * entirely empty deck is already too late, because the next swipe session
     * has nothing to show. Recycling decks never qualify, because they never run
     * out.
     */
    public static final int REFILL_THRESHOLD = 10;

    private static final String IMPORT_PATH_FORMAT = "/api/todo/tinder/decks/%s/entries";

    /** The one line handed over to run, as a native PowerShell script (Windows first). */
    private static final String RUN_LINE = ".\\scripts\\tinder-refill.ps1";

    private TinderPrompts() {
    }

    /** The absolute URL of a deck's import endpoint. */
    public static String importUrl(String apiBaseUrl, String deckKey) {
        String base = apiBaseUrl == null ? "" : apiBaseUrl;
        return base + String.format(IMPORT_PATH_FORMAT, deckKey);
    }

    /**
     * True when this deck should be offering a refill prompt: a deplete deck
     * that is down to {@link #REFILL_THRESHOLD} cards or fewer for the caller.
     *
     * <p>The count is per caller, which is the right unit: an idea deck depletes
     * per person, so it can be dry for one of them and full for the other, and
     * the person looking at an empty deck is the one who should be offered the
     * refill.
     */
    public static boolean needsRefill(boolean recycles, int remaining) {
        return !recycles && remaining <= REFILL_THRESHOLD;
    }

    /**
     * The prompt itself. Names the deck, its metadata key set, how many cards to
     * generate and the exact endpoint, which are the four things a session needs
     * to produce a batch that imports cleanly instead of one that dedupes to
     * nothing or carries a parallel metadata vocabulary.
     */
    public static String refill(String deckKey, String displayName, List<String> metadataKeys,
                                int count, String apiBaseUrl) {
        String keys = (metadataKeys == null || metadataKeys.isEmpty())
                ? "none yet: this deck's cards carry an empty metadata object, so use {} unless "
                        + "Patrick tells you which keys he wants"
                : String.join(", ", metadataKeys);

        StringBuilder p = new StringBuilder();
        p.append("Refill the TodoTinder deck \"").append(displayName).append("\".\n\n");
        p.append("Deck key: ").append(deckKey).append('\n');
        p.append("Cards to generate: ").append(count).append('\n');
        p.append("Metadata keys already in use on this deck: ").append(keys).append("\n\n");

        p.append("Write ").append(count).append(" NEW cards for this deck. Each card is a JSON ")
                .append("object with a \"text\" field (the line shown while swiping) and a ")
                .append("\"metadata\" object using exactly the keys listed above, no more and no ")
                .append("fewer. Match the language and the tone of the cards already in the deck.")
                .append("\n\n");

        p.append("Save the batch to a file called refill.json, shaped exactly like this:\n\n");
        p.append("{\"source\":\"claude-refill\",\"entries\":[{\"text\":\"...\",\"metadata\":{}}]}\n\n");

        p.append("Then hand Patrick this one line to run from the root of his TodoList checkout. ")
                .append("It prompts for the API token and the file path, so there is nothing in it ")
                .append("for him to edit:\n\n");
        p.append(RUN_LINE).append("\n\n");

        p.append("Do not ask Patrick for the API token and never put a token in a command: the ")
                .append("script above is the only thing allowed to collect it. For reference, the ")
                .append("line ends up posting the file to ")
                .append(importUrl(apiBaseUrl, deckKey))
                .append(". That endpoint dedupes on (deck, card text), so an accidental repeat is ")
                .append("skipped rather than duplicated, but a batch of mostly repeats wastes the ")
                .append("round trip: generate cards that are genuinely new.");

        return p.toString();
    }
}
