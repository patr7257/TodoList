package dk.dtu.methods;

import java.util.prefs.Preferences;

/**
 * Configuration for main application users who get quick-login buttons.
 */
public class MainUserConfig {
    
    private static final String PREF_MAIN_USER_1 = "mainUser1";
    private static final String PREF_MAIN_USER_2 = "mainUser2";
    private static final String DEFAULT_USER_1 = "Alice";
    private static final String DEFAULT_USER_2 = "Bob";
    
    private MainUserConfig() {}
    
    public static String getMainUser1() {
        Preferences prefs = Preferences.userNodeForPackage(MainUserConfig.class);
        String user = prefs.get(PREF_MAIN_USER_1, null);
        return (user != null && !user.isBlank()) ? user : DEFAULT_USER_1;
    }
    
    public static String getMainUser2() {
        Preferences prefs = Preferences.userNodeForPackage(MainUserConfig.class);
        String user = prefs.get(PREF_MAIN_USER_2, null);
        return (user != null && !user.isBlank()) ? user : DEFAULT_USER_2;
    }
    
    public static void setMainUser1(String username) {
        Preferences prefs = Preferences.userNodeForPackage(MainUserConfig.class);
        if (username == null || username.isBlank()) {
            prefs.remove(PREF_MAIN_USER_1);
        } else {
            prefs.put(PREF_MAIN_USER_1, username);
        }
    }
    
    public static void setMainUser2(String username) {
        Preferences prefs = Preferences.userNodeForPackage(MainUserConfig.class);
        if (username == null || username.isBlank()) {
            prefs.remove(PREF_MAIN_USER_2);
        } else {
            prefs.put(PREF_MAIN_USER_2, username);
        }
    }
    
    /**
     * Check if user is configured as a main user
     */
    public static boolean isMainUser(String username) {
        if (username == null || username.isBlank()) return false;
        return username.equals(getMainUser1()) || username.equals(getMainUser2());
    }
}
