package dk.dtu;

import java.util.Optional;
import java.util.prefs.Preferences;

// Persists the last server the client successfully connected to, so the next
// launch can default to it instead of always falling back to the baked-in
// default (or forcing the user to re-type the address every time).
public final class ServerPrefs {

    private static final String KEY_IP = "server.ip";
    private static final String KEY_PORT = "server.port";

    private static final Preferences PREFS = Preferences.userNodeForPackage(ClientApp.class);

    private ServerPrefs() {}

    // Remember the given server address as the last known good connection.
    // Call this only after a connection has actually succeeded.
    public static void save(String ip, int port) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        PREFS.put(KEY_IP, ip.trim());
        PREFS.putInt(KEY_PORT, port);
    }

    // The last saved server IP, if one was ever recorded.
    public static Optional<String> savedIp() {
        String ip = PREFS.get(KEY_IP, null);
        return (ip == null || ip.isBlank()) ? Optional.empty() : Optional.of(ip);
    }

    // The last saved server port, or the given fallback if nothing was saved yet.
    public static int savedPort(int fallback) {
        return PREFS.getInt(KEY_PORT, fallback);
    }

    // Forget the saved server address (not currently wired to any UI, kept for completeness).
    public static void clear() {
        PREFS.remove(KEY_IP);
        PREFS.remove(KEY_PORT);
    }
}
