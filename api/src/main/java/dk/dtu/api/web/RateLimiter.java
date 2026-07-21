package dk.dtu.api.web;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window, in-memory rate limiter, mirroring the website's project-gate
 * limiter (default 8 attempts per 10 minutes per key). The website degrades
 * gracefully when Redis is absent; this in-memory version is always available
 * for a single API instance. A max of 0 or less disables limiting.
 */
public final class RateLimiter {

    private final int maxAttempts;
    private final long windowMillis;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int maxAttempts, int windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowSeconds * 1000L;
    }

    /** Returns true if the request is allowed, false if the limit is exceeded. */
    public boolean allow(String key) {
        if (maxAttempts <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        Window w = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.start >= windowMillis) {
                return new Window(now, 1);
            }
            existing.count++;
            return existing;
        });
        return w.count <= maxAttempts;
    }

    private static final class Window {
        final long start;
        int count;

        Window(long start, int count) {
            this.start = start;
            this.count = count;
        }
    }
}
