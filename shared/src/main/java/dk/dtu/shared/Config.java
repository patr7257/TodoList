package dk.dtu.shared;

import java.util.function.Consumer;

// Client HTTP configuration: the base URL of the shared todo API, and the
// client-side connection error hooks used to surface connectivity problems.
public final class Config {

    // Connection error handler (for client only)
    private static volatile Consumer<Exception> connectionErrorHandler = null;

    // HTTP API CONFIGURATION (client only)
    // The JavaFX desktop client talks to the shared HTTP todo API. The base URL
    // is runtime-configurable so the packaged app can point at any deployment:
    // - System property:      -Dtodolist.api.url=<baseUrl>
    // - Environment variable:  TODOLIST_API_URL
    // Default is the public production API. The value is the origin only, with no
    // trailing "/api/todo" path (the client appends that itself).
    public static final String DEFAULT_API_BASE_URL = "https://api.todolist.patrickrobel.dk";

    public static String getApiBaseUrl() {
        String url = System.getProperty("todolist.api.url");
        if (url == null || url.isBlank()) {
            url = System.getenv("TODOLIST_API_URL");
        }
        return (url == null || url.isBlank()) ? DEFAULT_API_BASE_URL : url.trim();
    }

    // WEB ORIGIN CONFIGURATION (client only)
    // Sign in is browser mediated (issue #51): the desktop client opens a page on
    // the website, which owns the passkey and magic-link stack, and exchanges the
    // returned code for a session token. Same override pattern as the API URL, so
    // the client can be pointed at a local "next dev" during development:
    // - System property:      -Dtodolist.web.url=<baseUrl>
    // - Environment variable:  TODOLIST_WEB_URL
    // The value is the origin only, with no trailing path.
    public static final String DEFAULT_WEB_BASE_URL = "https://patrickrobel.dk";

    public static String getWebBaseUrl() {
        String url = System.getProperty("todolist.web.url");
        if (url == null || url.isBlank()) {
            url = System.getenv("TODOLIST_WEB_URL");
        }
        return (url == null || url.isBlank()) ? DEFAULT_WEB_BASE_URL : url.trim();
    }

    // Connection error handling (client-side)
    public static void setConnectionErrorHandler(Consumer<Exception> handler) {
        connectionErrorHandler = handler;
    }

    public static void handleConnectionError(Exception e) {
        if (connectionErrorHandler != null) {
            connectionErrorHandler.accept(e);
        } else {
            System.err.println("Connection error (no handler registered): " + e.getMessage());
        }
    }

    public static boolean isConnectionError(Exception e) {
        if (e == null) return false;
        String msg = e.getMessage();
        if (msg == null) msg = "";
        String lowerMsg = msg.toLowerCase();
        return lowerMsg.contains("connection") ||
               lowerMsg.contains("refused") ||
               lowerMsg.contains("timeout") ||
               lowerMsg.contains("unreachable") ||
               lowerMsg.contains("network") ||
               e instanceof java.net.ConnectException ||
               e instanceof java.net.SocketException ||
               e instanceof java.io.IOException;
    }

    // Private constructor to prevent instantiation
    private Config() {}
}
