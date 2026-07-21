package dk.dtu;

import atlantafx.base.theme.Styles;
import dk.dtu.methods.DataManagement;
import dk.dtu.shared.Config;
import dk.dtu.ui.Icons;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;

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
    private Button saveButton;
    private Button loadButton;
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

        // Save button (export data)
        saveButton = createIconButton(Icons.save(), "Save/Export");
        saveButton.setOnAction(e -> showSaveDialog());

        // Load button (import data)
        loadButton = createIconButton(Icons.load(), "Load Files");
        loadButton.setOnAction(e -> showLoadDialog());

        // Back button (always last)
        backButton = createIconButton(Icons.back(), "Go Back");
        backButton.setVisible(false); // Hidden by default

        // Add buttons in order: Home, Theme, Column filter, List filter, Save, Load, Settings, Back
        this.getChildren().addAll(homeButton, themeToggleButton, columnFilterButton, listFilterButton, saveButton, loadButton, settingsButton, backButton);
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
            
            ButtonType mainMenuButton = new ButtonType("Back to Main Menu");
            ButtonType logoutButton = new ButtonType("Logout");
            ButtonType cancelButton = ButtonType.CANCEL;
            
            alert.getButtonTypes().setAll(mainMenuButton, logoutButton, cancelButton);
            
            alert.showAndWait().ifPresent(response -> {
                if (response == mainMenuButton) {
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
    
    private void showSaveDialog() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Session Data");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("JSON Files", "*.json")
        );
        fileChooser.setInitialFileName("todolist-session.json");
        
        File file = fileChooser.showSaveDialog(this.getScene().getWindow());
        if (file != null) {
            String filePath = file.getAbsolutePath();
            
            // Show loading indicator
            Alert loadingAlert = new Alert(Alert.AlertType.INFORMATION);
            prepare(loadingAlert);
            loadingAlert.setTitle("Exporting");
            loadingAlert.setHeaderText("Exporting session data...");
            loadingAlert.setContentText("Please wait...");
            loadingAlert.show();
            
            // Export in background thread
            new Thread(() -> {
                DataManagement.exportSession(
                    Config.getRequestsUri(),
                    Config.getResponsesUri(),
                    filePath,
                    (message) -> Platform.runLater(() -> {
                        loadingAlert.close();
                        Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                        prepare(successAlert);
                        successAlert.setTitle("Export Successful");
                        successAlert.setHeaderText("Session data exported");
                        successAlert.setContentText("File saved to:\n" + filePath);
                        successAlert.showAndWait();
                    }),
                    (error) -> Platform.runLater(() -> {
                        loadingAlert.close();
                        Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                        prepare(errorAlert);
                        errorAlert.setTitle("Export Failed");
                        errorAlert.setHeaderText("Could not export session");
                        errorAlert.setContentText(error);
                        errorAlert.showAndWait();
                    })
                );
            }, "export-thread").start();
        }
    }
    
    private void showLoadDialog() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load Session Data");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("JSON Files", "*.json")
        );
        
        File file = fileChooser.showOpenDialog(this.getScene().getWindow());
        if (file != null) {
            String filePath = file.getAbsolutePath();
            
            // Ask user: Merge or Replace?
            Alert modeAlert = new Alert(Alert.AlertType.CONFIRMATION);
            prepare(modeAlert);
            modeAlert.setTitle("Import Mode");
            modeAlert.setHeaderText("Choose import mode");
            modeAlert.setContentText("File: " + file.getName() + "\n\n" +
                    "REPLACE: Remove all current data and load the file (no duplicates)\n\n" +
                    "MERGE: Keep current data and add new items from the file (duplicates are skipped)\n\n" +
                    "Which mode do you want to use?");
            
            ButtonType replaceButton = new ButtonType("Replace");
            ButtonType mergeButton = new ButtonType("Merge");
            ButtonType cancelButton = ButtonType.CANCEL;
            
            modeAlert.getButtonTypes().setAll(replaceButton, mergeButton, cancelButton);
            
            modeAlert.showAndWait().ifPresent(response -> {
                if (response == cancelButton) {
                    return;
                }
                
                String mode = (response == mergeButton) ? "merge" : "replace";
                String actionDesc = (response == mergeButton) ? "Merging" : "Replacing";
                
                // Show loading indicator
                Alert loadingAlert = new Alert(Alert.AlertType.INFORMATION);
                prepare(loadingAlert);
                loadingAlert.setTitle(actionDesc);
                loadingAlert.setHeaderText(actionDesc + " session data...");
                loadingAlert.setContentText("Please wait...");
                loadingAlert.show();
                
                // Import in background thread
                new Thread(() -> {
                    DataManagement.importSession(
                        Config.getRequestsUri(),
                        Config.getResponsesUri(),
                        filePath,
                        mode,
                        (message) -> Platform.runLater(() -> {
                            loadingAlert.close();
                            Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                            prepare(successAlert);
                            successAlert.setTitle("Import Successful");
                            successAlert.setHeaderText("Session data " + (mode.equals("merge") ? "merged" : "imported"));
                            successAlert.setContentText("Data loaded from:\n" + filePath + "\n\n" +
                                    "Please refresh your views to see the new data.");
                            successAlert.showAndWait();
                            
                            // Navigate to main menu to force refresh
                            if (navigator.getCurrentUser() != null) {
                                navigator.showMainMenu();
                            }
                        }),
                        (error) -> Platform.runLater(() -> {
                            loadingAlert.close();
                            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                            prepare(errorAlert);
                            errorAlert.setTitle("Import Failed");
                            errorAlert.setHeaderText("Could not import session");
                            errorAlert.setContentText(error);
                            errorAlert.showAndWait();
                        })
                    );
                }, "import-thread").start();
            });
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
