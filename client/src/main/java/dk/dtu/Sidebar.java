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
    private Button themeToggleButton;
    private Button columnFilterButton;
    private Button listFilterButton;
    private Button backButton;
    
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

        // Theme toggle button (sun/moon icon)
        themeToggleButton = createThemeToggleButton();
        themeToggleButton.setOnAction(e -> toggleTheme());
        
        // Column filter button (choose/rearrange visible columns)
        columnFilterButton = createIconButton("/Icons/RearrangeColumn.png", "Choose columns");
        columnFilterButton.setVisible(false);

        // List/task filter button (filter visible items)
        listFilterButton = createIconButton("/Icons/filter.png", "Filter");
        listFilterButton.setVisible(false);

        // Back button (always last)
        backButton = createIconButton("/Icons/gobackicon.png", "Go Back");

        // Add buttons in requested order:
        // Home, Dark/Light mode, Column filter, List filter, Go Back
        this.getChildren().addAll(homeButton, themeToggleButton, columnFilterButton, listFilterButton, backButton);
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

    /**
     * Backwards-compatible alias: previously the single filter button was used for "Choose columns".
     */
    public void setFilterButtonAction(Runnable action) {
        setColumnFilterButtonAction(action);
    }

    public void setColumnFilterButtonAction(Runnable action) {
        if (action != null) {
            columnFilterButton.setOnAction(e -> action.run());
            columnFilterButton.setVisible(true);
        } else {
            columnFilterButton.setVisible(false);
        }
    }

    public void setListFilterButtonAction(Runnable action) {
        if (action != null) {
            listFilterButton.setOnAction(e -> action.run());
            listFilterButton.setVisible(true);
        } else {
            listFilterButton.setVisible(false);
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
