package dk.dtu;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

/**
 * Sidebar component that persists across all scenes
 * Contains navigation buttons and theme toggle
 */
public class Sidebar extends VBox {
    
    private final SceneNavigator navigator;
    private Button homeButton;
    private Button backButton;
    private Button themeToggleButton;
    
    private boolean isDarkMode = false;
    private Runnable onThemeChange;
    
    public Sidebar(SceneNavigator navigator) {
        this.navigator = navigator;
        
        // Sidebar styling
        this.setAlignment(Pos.TOP_CENTER);
        this.setPadding(new Insets(15, 10, 15, 10));
        this.setSpacing(15);
        this.getStyleClass().add("sidebar");
        this.setPrefWidth(70);
        this.setMinWidth(70);
        this.setMaxWidth(70);
        
        initializeButtons();
    }
    
    private void initializeButtons() {
        // Home button (top button - always visible)
        homeButton = createIconButton("/Icons/homeicon.png", "Home");
        homeButton.setOnAction(e -> {
            if (navigator.getCurrentUser() != null) {
                navigator.showMainMenu();
            } else {
                navigator.showWelcome();
            }
        });
        
        // Back button (second button - contextual)
        backButton = createIconButton("/Icons/gobackicon.png", "Go Back");
        
        // Theme toggle button (sun/moon icon)
        themeToggleButton = createThemeToggleButton();
        themeToggleButton.setOnAction(e -> toggleTheme());
        
        // Add spacer to push theme toggle to bottom
        VBox spacer = new VBox();
        VBox.setVgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        
        // Add buttons to sidebar with spacer between navigation and theme toggle
        this.getChildren().addAll(homeButton, backButton, spacer, themeToggleButton);
    }
    
    private Button createIconButton(String iconPath, String tooltipText) {
        Button button = new Button();
        try {
            ImageView icon = new ImageView(new Image(getClass().getResourceAsStream(iconPath)));
            icon.setFitWidth(32);
            icon.setFitHeight(32);
            button.setGraphic(icon);
        } catch (Exception e) {
            button.setText("?");
        }
        
        button.getStyleClass().add("sidebar-button");
        button.setTooltip(new Tooltip(tooltipText));
        button.setPrefSize(50, 50);
        
        return button;
    }
    
    private Button createThemeToggleButton() {
        Button button = new Button();
        updateThemeIcon(button);
        button.getStyleClass().add("sidebar-button");
        button.setTooltip(new Tooltip("Toggle Dark Mode"));
        button.setPrefSize(50, 50);
        return button;
    }
    
    private void updateThemeIcon(Button button) {
        // Show moon when in light mode (to switch to dark), sun when in dark mode (to switch to light)
        if (isDarkMode) {
            button.setText("☀");
            button.setStyle("-fx-font-size: 24px; -fx-text-fill: #ffd700;");
            button.setTooltip(new Tooltip("Switch to Light Mode"));
        } else {
            button.setText("🌙");
            button.setStyle("-fx-font-size: 24px; -fx-text-fill: #4a5568;");
            button.setTooltip(new Tooltip("Switch to Dark Mode"));
        }
    }
    
    private void toggleTheme() {
        isDarkMode = !isDarkMode;
        updateThemeIcon(themeToggleButton);
        
        if (onThemeChange != null) {
            onThemeChange.run();
        }
    }
    
    public void setBackButtonAction(Runnable action) {
        if (action != null) {
            backButton.setOnAction(e -> action.run());
            backButton.setVisible(true);
        } else {
            backButton.setVisible(false);
        }
    }
    
    public void setOnThemeChange(Runnable callback) {
        this.onThemeChange = callback;
    }
    
    public boolean isDarkMode() {
        return isDarkMode;
    }
    
    public void hideBackButton() {
        backButton.setVisible(false);
    }
    
    public void showBackButton() {
        backButton.setVisible(true);
    }
}
