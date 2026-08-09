package dk.dtu.auth;

import java.awt.Desktop;
import java.net.URI;

/**
 * Opening a URL in the user's own browser, in one guarded place.
 *
 * <p>Best effort by design: {@link Desktop} is unavailable on a headless JVM and
 * can throw for reasons we cannot fix from here (no registered handler, a
 * sandbox), so every failure is swallowed and reported as {@code false}. Callers
 * fall back to showing the URL instead.
 */
public final class Browsers {

    private Browsers() {
    }

    /** Opens {@code url} in the system browser. Returns false if it could not. */
    public static boolean open(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return true;
            }
        } catch (Throwable ignored) {
            // Nothing else we can do from here.
        }
        return false;
    }
}
