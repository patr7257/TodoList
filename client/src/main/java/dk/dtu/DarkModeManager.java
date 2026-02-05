package dk.dtu;

import javafx.scene.Scene;

/**
 * Manages dark mode theme switching for the application
 */
public class DarkModeManager {
    
    private static final String COMMON_CSS = "/common.css";
    private static final String DARK_MODE_CSS = "/dark-mode.css";
    
    private boolean isDarkMode = false;
    private Scene currentScene;
    
    public DarkModeManager() {
    }
    
    public void setScene(Scene scene) {
        this.currentScene = scene;
        applyTheme();
    }
    
    public void toggleDarkMode() {
        isDarkMode = !isDarkMode;
        applyTheme();
    }
    
    public boolean isDarkMode() {
        return isDarkMode;
    }
    
    private void applyTheme() {
        if (currentScene == null) {
            return;
        }
        
        currentScene.getStylesheets().clear();
        
        // Always add common.css first
        String commonCss = getClass().getResource(COMMON_CSS).toExternalForm();
        currentScene.getStylesheets().add(commonCss);
        
        // Add dark mode CSS if enabled
        if (isDarkMode) {
            try {
                String darkCss = getClass().getResource(DARK_MODE_CSS).toExternalForm();
                currentScene.getStylesheets().add(darkCss);
            } catch (Exception e) {
                System.err.println("Could not load dark mode CSS: " + e.getMessage());
            }
        }
    }
}
