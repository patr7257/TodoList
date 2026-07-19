package dk.dtu.ui;

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
}
