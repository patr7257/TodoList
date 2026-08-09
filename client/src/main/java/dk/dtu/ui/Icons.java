package dk.dtu.ui;

import java.util.List;

import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Central factory for the app's vector icons (Ikonli Feather).
 *
 * Every icon is a {@link FontIcon} carrying the {@code app-icon} style class so
 * its color follows the theme via {@code -fx-icon-color} in common.css (light or
 * dark), instead of the old fixed-color PNGs that vanished in dark mode.
 *
 * Icons are created from Feather literal strings ("fth-...") so we do not need a
 * compile-time dependency on the generated icon enum. See the Feather set for the
 * full list of literals.
 */
public final class Icons {

    /** Default sidebar icon size, in px. */
    public static final int SIDEBAR = 20;
    /** Default in-row (table cell) icon size, in px. */
    public static final int ROW = 16;

    private Icons() {
    }

    /** Build a themed icon from a Feather literal (e.g. "fth-home") at the given size. */
    public static FontIcon of(String literal, int size) {
        FontIcon icon = new FontIcon(literal);
        icon.setIconSize(size);
        icon.getStyleClass().add("app-icon");
        return icon;
    }

    // --- Fun counters ------------------------------------------------------

    /**
     * Ikonli Feather is the only icon pack bundled; it has no plane, ship, or
     * walking glyph, so these are the fixed substitutes for the fun counters
     * (Total Flights, Total Ships, Tour de Brede). Any icon picker for a
     * counter must offer only literals from this whitelist.
     */
    public static final List<String> COUNTER_ICON_CHOICES = List.of("fth-send", "fth-anchor", "fth-compass");

    /** Neutral fallback glyph used when a counter's icon is missing or unrecognized. */
    private static final String FALLBACK_ICON = "fth-hash";

    /**
     * Build an icon from an arbitrary, possibly hand-edited, blank, or unknown
     * literal. {@code new FontIcon(literal)} throws for a literal Ikonli does
     * not recognize; this NEVER throws, falling back to a neutral glyph
     * instead, so a bad {@code icon} column value can never blank a tile or
     * crash the dashboard (the one screen every login must land on safely).
     */
    public static FontIcon safe(String literal, int size) {
        String candidate = (literal == null || literal.isBlank()) ? FALLBACK_ICON : literal.trim();
        try {
            return of(candidate, size);
        } catch (Exception e) {
            try {
                return of(FALLBACK_ICON, size);
            } catch (Exception fallbackFailed) {
                return null; // extremely defensive; callers must null-check the graphic
            }
        }
    }

    // --- Sidebar ---------------------------------------------------------------
    public static FontIcon home()     { return of("fth-home", SIDEBAR); }
    public static FontIcon columns()  { return of("fth-columns", SIDEBAR); }
    public static FontIcon filter()   { return of("fth-filter", SIDEBAR); }
    public static FontIcon settings() { return of("fth-settings", SIDEBAR); }
    public static FontIcon save()     { return of("fth-download", SIDEBAR); }
    public static FontIcon load()     { return of("fth-upload", SIDEBAR); }
    public static FontIcon back()     { return of("fth-arrow-left", SIDEBAR); }
    public static FontIcon sun()      { return of("fth-sun", SIDEBAR); }
    public static FontIcon moon()     { return of("fth-moon", SIDEBAR); }

    // --- Table rows ------------------------------------------------------------
    public static FontIcon delete()   { return of("fth-trash-2", ROW); }
    public static FontIcon reorder()  { return of("fth-menu", ROW); }

    // --- Branding / misc -------------------------------------------------------
    public static FontIcon checklist(int size) { return of("fth-check-square", size); }

    // --- Sharing (issue #52) -----------------------------------------------
    public static FontIcon share() { return of("fth-share-2", ROW); }
    public static FontIcon copy()  { return of("fth-copy", ROW); }
}
