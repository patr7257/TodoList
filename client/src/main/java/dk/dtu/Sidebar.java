package dk.dtu;

import atlantafx.base.theme.Styles;
import dk.dtu.ui.Icons;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

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
    private Button settingsButton;
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
        // Home button (top button - always visible, logout functionality)
        homeButton = createIconButton(Icons.home(), "Home");
        homeButton.setOnAction(e -> showHomeDialog());

        // Theme toggle button (sun/moon icon)
        themeToggleButton = createThemeToggleButton();
        themeToggleButton.setOnAction(e -> toggleTheme());

        // Column filter button (choose/rearrange visible columns)
        columnFilterButton = createIconButton(Icons.columns(), "Columns");
        columnFilterButton.setDisable(true); // Always visible but disabled by default

        // List/task filter button (filter visible items)
        listFilterButton = createIconButton(Icons.filter(), "Filter");
        listFilterButton.setDisable(true); // Always visible but disabled by default

        // Settings button
        settingsButton = createIconButton(Icons.settings(), "Settings");
        settingsButton.setOnAction(e -> showSettingsDialog());

        // Back button (always last)
        backButton = createIconButton(Icons.back(), "Go Back");
        backButton.setVisible(false); // Hidden by default

        // Add buttons in order: Home, Theme, Column filter, List filter, Settings, Back
        this.getChildren().addAll(homeButton, themeToggleButton, columnFilterButton, listFilterButton, settingsButton, backButton);
    }
    
    private Button createIconButton(FontIcon graphic, String tooltipText) {
        Button button = new Button();
        button.setGraphic(graphic);

        button.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, "sidebar-button");
        Tooltip tooltip = new Tooltip(tooltipText);
        button.setTooltip(tooltip);
        button.setPrefSize(50, 50);

        return button;
    }

    private Button createThemeToggleButton() {
        Button button = new Button();
        button.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, "sidebar-button", "theme-toggle-button");
        button.setPrefSize(50, 50);
        updateThemeIcon(button);
        return button;
    }
    
    /** Owner + window-modality + brand/dark styling for a dialog or alert. */
    private void prepare(Dialog<?> dialog) {
        DarkModeManager.prepareDialog(dialog, DarkModeManager.windowOf(this));
    }

    private void showHomeDialog() {
        if (navigator.getCurrentUser() != null) {
            // User is logged in - show options dialog
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            prepare(alert);
            alert.setTitle("Home Options");
            alert.setHeaderText("Choose an action");
            alert.setContentText("What would you like to do?");
            
            ButtonType frontpageButton = new ButtonType("Frontpage");
            ButtonType mainMenuButton = new ButtonType("Back to Main Menu");
            ButtonType logoutButton = new ButtonType("Logout");
            ButtonType cancelButton = ButtonType.CANCEL;

            alert.getButtonTypes().setAll(frontpageButton, mainMenuButton, logoutButton, cancelButton);

            alert.showAndWait().ifPresent(response -> {
                if (response == frontpageButton) {
                    navigator.showDashboard();
                } else if (response == mainMenuButton) {
                    navigator.showMainMenu();
                } else if (response == logoutButton) {
                    navigator.setCurrentUser(null);
                    navigator.showLogin();
                }
                // Cancel does nothing
            });
        } else {
            // Not logged in - go to welcome screen
            navigator.showWelcome();
        }
    }
    
    private void showSettingsDialog() {
        SettingsDialog dialog = new SettingsDialog();
        prepare(dialog);
        dialog.setOnSettingsChanged(() -> {
            // Refresh current scene if needed - settings were changed
            System.out.println("Settings changed - restart scenes to see changes");
            navigator.refreshWindowTitle();
        });
        dialog.showAndWait();
    }
    
    private void updateThemeIcon(Button button) {
        // Show sun when in dark mode (to switch to light), moon when in light mode (to switch to dark)
        button.setText(null);
        if (isDarkMode) {
            button.setGraphic(Icons.sun());
            button.setTooltip(new Tooltip("Light Mode"));
        } else {
            button.setGraphic(Icons.moon());
            button.setTooltip(new Tooltip("Dark Mode"));
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
            columnFilterButton.setDisable(false);
        } else {
            columnFilterButton.setDisable(true);
        }
    }

    public void setListFilterButtonAction(Runnable action) {
        if (action != null) {
            listFilterButton.setOnAction(e -> action.run());
            listFilterButton.setDisable(false);
        } else {
            listFilterButton.setDisable(true);
        }
    }
    
    public void setOnThemeChange(Runnable callback) {
        this.onThemeChange = callback;
    }
    
    public boolean isDarkMode() {
        return isDarkMode;
    }
    
    public void disableBackButton() {
        backButton.setDisable(true);
    }
    
    public void enableBackButton() {
        backButton.setDisable(false);
    }
}
