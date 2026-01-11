package dk.dtu;

import org.jspace.FormalField;
import org.jspace.Space;

public final class ServerConfig {
	public static final String HOST = "127.0.0.1";
	public static final int PORT = 9001;

	private ServerConfig() {
	}

	public static String gateUri() {
		return "tcp://" + HOST + ":" + PORT + "/?keep";
	}

	public static String spaceUri(String spaceName) {
		return "tcp://" + HOST + ":" + PORT + "/" + spaceName + "?keep";
	}

    // Used as counter for todo lists
	public static int getTodoListCount(Space todoLists) throws InterruptedException {
		return todoLists.queryAll(
				new FormalField(String.class),
				new FormalField(String.class)
		).size();
	}

    // Make sure counter is in sync with actual todo lists
	public static void syncCounterToTodoLists(Space counter, Space todoLists) throws InterruptedException {
		int count = getTodoListCount(todoLists);
		counter.getp(new FormalField(Integer.class));
		counter.put(count);
	}
}
