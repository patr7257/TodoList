package dk.dtu.shared;

// Shared configuration class for server and client
public final class Config {
    
    // Server IP: The IP address where the server is hosted
    // If running locally on own maching:
    public static final String SERVER_IP = "127.0.0.1";
    
    // JOHAN HOME:
    //public static final String SERVER_IP = "127.0.0.1";

    // DTU Secure:
    //public static final String SERVER_IP = "127.0.0.1";

    // VPN to DTU:
    //public static final String SERVER_IP = "127.0.0.1";

    
    public static final int PORT = 9001;                        // Port for jSpace server
    public static final String SERVER_BIND_HOST = "0.0.0.0";    // Bind to all interfaces
    
    // Server gate URI for jSpace
    public static String getServerGateUri() {
        return "tcp://" + SERVER_BIND_HOST + ":" + PORT + "/?keep";
    }
    
    
    // CLIENT CONFIGURATION
    // Base URI for client connections
    public static String getClientBaseUri() {
        return "tcp://" + SERVER_IP + ":" + PORT + "/";
    }
    
    // Client URI for todoLists space
    public static String getTodoListsUri() {
        return getClientBaseUri() + "todoLists?keep";
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
