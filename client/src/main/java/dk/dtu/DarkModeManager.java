package dk.dtu;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.Scene;

/**
 * Manages theme switching for the application.
 *
 * Dark mode toggling swaps the GLOBAL AtlantaFX user-agent stylesheet between
 * PrimerLight and PrimerDark, so every window (including dialogs) follows the
 * theme automatically. A single slim brand overlay (common.css) is attached to
 * each scene on top of AtlantaFX to apply the violet/cyan accent and app styling.
 */
public class DarkModeManager {

    private static final String COMMON_CSS = "/common.css";

    private boolean isDarkMode = false;
    private Scene currentScene;

    public DarkModeManager() {
    }

    public void setScene(Scene scene) {
        this.currentScene = scene;
        attachBrandStylesheet(scene);
        applyTheme();
    }

    public void toggleDarkMode() {
        isDarkMode = !isDarkMode;
        applyTheme();
    }

    public boolean isDarkMode() {
        return isDarkMode;
    }

    /**
     * Attach the brand overlay stylesheet to a scene (idempotent).
     * Public so dialogs with their own scenes can share the brand accents.
     */
    public void attachBrandStylesheet(Scene scene) {
        if (scene == null) {
            return;
        }
        try {
            String commonCss = getClass().getResource(COMMON_CSS).toExternalForm();
            if (!scene.getStylesheets().contains(commonCss)) {
                scene.getStylesheets().add(commonCss);
            }
        } catch (Exception e) {
            System.err.println("Could not load brand stylesheet: " + e.getMessage());
        }
    }

    private void applyTheme() {
        // The user-agent stylesheet is global, so this affects all open windows.
        if (isDarkMode) {
            Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        } else {
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        }
    }
}
