package dk.dtu;

public final class Config {
	private Config() {}

	public static final String HOST = "127.0.0.1";
	public static final int PORT = 9001;

	public static final String BASE_URI = "tcp://" + HOST + ":" + PORT + "/";

	public static final String TODO_LISTS_URI = BASE_URI + "todoLists?keep";
	public static final String USERS_URI = BASE_URI + TupleSpaces.USERS + "?keep";

	public static final String TASKS_URI = BASE_URI + TupleSpaces.TASKS + "?keep";
	public static final String REQUESTS_URI = BASE_URI + TupleSpaces.REQUESTS + "?keep";
	public static final String RESPONSES_URI = BASE_URI + TupleSpaces.RESPONSES + "?keep";
}
