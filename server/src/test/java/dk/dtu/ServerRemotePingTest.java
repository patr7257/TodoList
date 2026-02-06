package dk.dtu;

import dk.dtu.shared.Config;
import dk.dtu.shared.TupleSpaces;
import org.jspace.ActualField;
import org.jspace.FormalField;
import org.jspace.RemoteSpace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ServerRemotePingTest {

    @AfterEach
    void clearProps() {
        System.clearProperty("todolist.server.ip");
        System.clearProperty("todolist.bind.host");
        System.clearProperty("todolist.port");
    }

    @Test
    void clientCanPingOverTcpGate() throws Exception {
        // Use a non-default port to avoid conflicting with a running server.
        System.setProperty("todolist.server.ip", "127.0.0.1");
        System.setProperty("todolist.bind.host", "127.0.0.1");
        System.setProperty("todolist.port", "19001");

        try (ServerEngine engine = ServerEngine.start()) {
            RemoteSpace requests = new RemoteSpace(Config.getRequestsUri());
            RemoteSpace responses = new RemoteSpace(Config.getResponsesUri());

            String requestId = UUID.randomUUID().toString();
            requests.put(TupleSpaces.CMD_PING, requestId, "", "", "", "");

            Object[] resp = waitForResponse(responses, requestId, Duration.ofSeconds(3));
            assertNotNull(resp, "Expected a response tuple");
            assertEquals(TupleSpaces.RESP_OK, resp[0]);
            assertEquals(requestId, resp[1]);
            assertEquals("pong", String.valueOf(resp[2]));
        }
    }

    private static Object[] waitForResponse(RemoteSpace responses, String requestId, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Object[] tuple = responses.getp(
                    new FormalField(Object.class),
                    new ActualField(requestId),
                    new FormalField(Object.class),
                    new FormalField(Object.class),
                    new FormalField(Object.class),
                    new FormalField(Object.class)
            );

            if (tuple != null) {
                return tuple;
            }

            Thread.sleep(25);
        }
        fail("Timed out waiting for response over TCP gate (" + Config.getClientBaseUri() + ")");
        return null;
    }
}
