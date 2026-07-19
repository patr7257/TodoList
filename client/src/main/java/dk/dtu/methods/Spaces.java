package dk.dtu.methods;

import org.jspace.RemoteSpace;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-scoped cache of jSpace {@link RemoteSpace} connections.
 *
 * The client used to do {@code new RemoteSpace(uri)} on every single operation,
 * so each action paid a fresh TCP connect + jSpace handshake (very slow over a
 * remote/Tailscale link). This keeps ONE open connection per space URI and reuses
 * it for the whole session.
 *
 * jSpace's remote connection is a single socket, so concurrent operations on the
 * same connection could interleave protocol messages. This app is used by a single
 * person at a time, so we simply serialize all request/response/query operations
 * through {@link #IO_LOCK}: every operation is fast (one round trip on an already
 * open connection), and serializing them keeps the shared sockets correct. The
 * notification listener's long-blocking {@code get} MUST stay on its own connection
 * OUTSIDE this lock (otherwise it would block every other operation forever).
 */
public final class Spaces {

    /** Serializes operations that share the cached connections. */
    public static final Object IO_LOCK = new Object();

    private static final Map<String, RemoteSpace> CACHE = new ConcurrentHashMap<>();

    private Spaces() {}

    /** Returns the cached connection for this URI, opening it once on first use. */
    public static RemoteSpace get(String uri) throws Exception {
        RemoteSpace s = CACHE.get(uri);
        if (s != null) {
            return s;
        }
        synchronized (CACHE) {
            s = CACHE.get(uri);
            if (s == null) {
                s = new RemoteSpace(uri);
                CACHE.put(uri, s);
            }
            return s;
        }
    }

    /**
     * Drops the cached connection for this URI so the next {@link #get} reopens it.
     * Call this when an operation failed with a connection error, so a dropped
     * socket is not reused.
     */
    public static void invalidate(String uri) {
        CACHE.remove(uri);
    }

    /** Forget all connections (e.g. on logout / disconnect). */
    public static void reset() {
        CACHE.clear();
    }
}
