package dk.dtu;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Configuration for main users (1 or 2 users shown as big buttons on login screen).
 * Stores configuration in Java Preferences API for persistence.
 */
public class MainUserConfig {
    private static final Preferences prefs = Preferences.userNodeForPackage(MainUserConfig.class);
    private static final String MAIN_USER_COUNT_KEY = "mainUserCount";
    private static final String MAIN_USER_1_KEY = "mainUser1";
    private static final String MAIN_USER_2_KEY = "mainUser2";
    private static final String MAIN_USER_1_COLOR_KEY = "mainUser1Color";
    private static final String MAIN_USER_2_COLOR_KEY = "mainUser2Color";
    
    // Default main users if not configured
    private static final String DEFAULT_USER_1 = "Alice";
    private static final String DEFAULT_USER_2 = "Bob";
    private static final String DEFAULT_COLOR_1 = "#29b6f6"; // Light blue
    private static final String DEFAULT_COLOR_2 = "#4caf50"; // Green
    
    /**
     * Get the number of main users (1 or 2)
     */
    public static int getMainUserCount() {
        return prefs.getInt(MAIN_USER_COUNT_KEY, 2); // Default to 2 users
    }
    
    /**
     * Set the number of main users (1 or 2)
     */
    public static void setMainUserCount(int count) {
        if (count < 1 || count > 2) {
            throw new IllegalArgumentException("Main user count must be 1 or 2");
        }
        prefs.putInt(MAIN_USER_COUNT_KEY, count);
    }
    
    /**
     * Get the first main user (always present if count >= 1)
     */
    public static String getMainUser1() {
        return prefs.get(MAIN_USER_1_KEY, DEFAULT_USER_1);
    }
    
    /**
     * Set the first main user
     */
    public static void setMainUser1(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        prefs.put(MAIN_USER_1_KEY, username.trim());
    }
    
    /**
     * Get the second main user (only relevant if count == 2)
     */
    public static String getMainUser2() {
        return prefs.get(MAIN_USER_2_KEY, DEFAULT_USER_2);
    }
    
    /**
     * Set the second main user
     */
    public static void setMainUser2(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        prefs.put(MAIN_USER_2_KEY, username.trim());
    }
    
    /**
     * Get all main users based on current count
     */
    public static List<String> getMainUsers() {
        List<String> users = new ArrayList<>();
        users.add(getMainUser1());
        if (getMainUserCount() == 2) {
            users.add(getMainUser2());
        }
        return users;
    }
    
    /**
     * Check if a username is a main user
     */
    public static boolean isMainUser(String username) {
        if (username == null) return false;
        return getMainUsers().contains(username);
    }
    
    /**
     * Get the color for the first main user button
     */
    public static String getMainUser1Color() {
        return prefs.get(MAIN_USER_1_COLOR_KEY, DEFAULT_COLOR_1);
    }
    
    /**
     * Set the color for the first main user button
     */
    public static void setMainUser1Color(String color) {
        if (color == null || color.trim().isEmpty()) {
            color = DEFAULT_COLOR_1;
        }
        prefs.put(MAIN_USER_1_COLOR_KEY, color.trim());
    }
    
    /**
     * Get the color for the second main user button
     */
    public static String getMainUser2Color() {
        return prefs.get(MAIN_USER_2_COLOR_KEY, DEFAULT_COLOR_2);
    }
    
    /**
     * Set the color for the second main user button
     */
    public static void setMainUser2Color(String color) {
        if (color == null || color.trim().isEmpty()) {
            color = DEFAULT_COLOR_2;
        }
        prefs.put(MAIN_USER_2_COLOR_KEY, color.trim());
    }
    
    /**
     * Get the style class for a main user button (based on position)
     */
    public static String getStyleClassForUser(String username) {
        if (username.equals(getMainUser1())) {
            return "main-user-button-alice"; // First user gets light blue
        } else if (username.equals(getMainUser2())) {
            return "main-user-button-bob"; // Second user gets green
        }
        return "main-user-button-alice"; // Default to light blue
    }
}
