package dk.dtu;

public final class ClientConfig {
	private ClientConfig() {
	}

	// Change this IP to match the server's LAN IP address!
	// Johans IP:
	public static final String SERVER_IP = "192.168.0.15";

	// Use this to run on own PC
	// public static final String SERVER_IP = "127.0.0.1";

	public static final String HOST = SERVER_IP;
	public static final int PORT = 9001;

	public static final String BASE_URI = "tcp://" + HOST + ":" + PORT + "/";

	public static final String TODO_LISTS_URI = BASE_URI + "todoLists?keep";
	public static final String USERS_URI = BASE_URI + TupleSpaces.USERS + "?keep";

	public static final String TASKS_URI = BASE_URI + TupleSpaces.TASKS + "?keep";
	public static final String REQUESTS_URI = BASE_URI + TupleSpaces.REQUESTS + "?keep";
	public static final String RESPONSES_URI = BASE_URI + TupleSpaces.RESPONSES + "?keep";
}
