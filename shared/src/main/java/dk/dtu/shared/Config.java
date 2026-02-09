package dk.dtu.shared;

import java.io.File;
import java.util.function.Consumer;

// Shared configuration class for server and client
public final class Config {

    // Connection error handler (for client only)
    private static volatile Consumer<Exception> connectionErrorHandler = null;

    // SERVER CONFIGURATION
    // Intentionally runtime-configurable so packaged apps can run on any PC.
    // System properties:
    // - Client connects to:  -Dtodolist.server.ip=<ip>   (default: 127.0.0.1)
    // - Server binds on:     -Dtodolist.bind.host=<host> (default: 0.0.0.0)
    // - Both use port:       -Dtodolist.port=<port>      (default: 9001)
    // Environment variable fallbacks are also supported:
    // - TODOLIST_SERVER_IP, TODOLIST_BIND_HOST, TODOLIST_PORT
    public static final String DEFAULT_SERVER_IP = "127.0.0.1";
    public static final int DEFAULT_PORT = 9001;
    public static final String DEFAULT_SERVER_BIND_HOST = "0.0.0.0";
    
    // DATA PERSISTENCE CONFIGURATION
    // Default data directory in user home folder (works for both development and MSI installations)
    // Can be overridden via system property: -Dtodolist.data.dir=/custom/path
    public static final String DEFAULT_DATA_DIR = System.getProperty("user.home") + File.separator + ".todolist-data";
    
    /**
     * Get the data directory path for storing session files.
     * Checks system property first, then falls back to default.
     * @return Path to data directory
     */
    public static String getDataDirectory() {
        return System.getProperty("todolist.data.dir", DEFAULT_DATA_DIR);
    }

    public static String getServerIp() {
        String ip = System.getProperty("todolist.server.ip");
        if (ip == null || ip.isBlank()) {
            ip = System.getenv("TODOLIST_SERVER_IP");
        }
        return (ip == null || ip.isBlank()) ? DEFAULT_SERVER_IP : ip.trim();
    }

    public static int getPort() {
        String portStr = System.getProperty("todolist.port");
        if (portStr == null || portStr.isBlank()) {
            portStr = System.getenv("TODOLIST_PORT");
        }
        if (portStr == null || portStr.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(portStr.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }

    public static String getServerBindHost() {
        String host = System.getProperty("todolist.bind.host");
        if (host == null || host.isBlank()) {
            host = System.getenv("TODOLIST_BIND_HOST");
        }
        return (host == null || host.isBlank()) ? DEFAULT_SERVER_BIND_HOST : host.trim();
    }

    // Server gate URI for jSpace
    public static String getServerGateUri() {
        return "tcp://" + getServerBindHost() + ":" + getPort() + "/?keep";
    }

    // CLIENT CONFIGURATION
    // Base URI for client connections
    public static String getClientBaseUri() {
        return "tcp://" + getServerIp() + ":" + getPort() + "/";
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
    
    // Client URI for todoLists space
    public static String getTodoListsUri() {
        return getClientBaseUri() + TupleSpaces.LISTS + "?keep";
    }
    
    // Client URI for users space
    public static String getUsersUri() {
        return getClientBaseUri() + TupleSpaces.USERS + "?keep";
    }
    
    // Client URI for tasks space
    public static String getTasksUri() {
        return getClientBaseUri() + TupleSpaces.TASKS + "?keep";
    }
    
    // Client URI for requests space
    public static String getRequestsUri() {
        return getClientBaseUri() + TupleSpaces.REQUESTS + "?keep";
    }
    
    // Client URI for responses space
    public static String getResponsesUri() {
        return getClientBaseUri() + TupleSpaces.RESPONSES + "?keep";
    }
    
    // Client URI for notifications space
    public static String getNotificationsUri() {
        return getClientBaseUri() + TupleSpaces.NOTIFICATIONS + "?keep";
    }
    
    // Private constructor to prevent instantiation
    private Config() {}
}
