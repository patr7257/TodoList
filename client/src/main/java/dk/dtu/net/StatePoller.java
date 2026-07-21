package dk.dtu.net;

import java.util.function.BooleanSupplier;

/**
 * Lightweight replacement for the old jSpace notification listener. The HTTP
 * API has no server push yet, so this polls on a fixed interval and asks the UI
 * to refresh the current view, mirroring the old "data changed -> refetch"
 * behavior.
 *
 * <p>It runs on its own daemon thread and never touches JavaFX state itself:
 * the supplied {@code onTick} callback is responsible for marshalling onto the
 * FX thread (the scenes' reload methods already do this via
 * {@code Platform.runLater}). Polling is skipped while {@code shouldPoll}
 * returns false (for example when the window is not focused), so a backgrounded
 * window does not hammer the API.
 */
public final class StatePoller implements Runnable {

    private final long intervalMillis;
    private final Runnable onTick;
    private final BooleanSupplier shouldPoll;
    private volatile boolean running = true;

    public StatePoller(long intervalMillis, Runnable onTick, BooleanSupplier shouldPoll) {
        this.intervalMillis = intervalMillis;
        this.onTick = onTick;
        this.shouldPoll = shouldPoll;
    }

    @Override
    public void run() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!running) {
                return;
            }
            try {
                if (shouldPoll == null || shouldPoll.getAsBoolean()) {
                    if (onTick != null) {
                        onTick.run();
                    }
                }
            } catch (Exception e) {
                // A tick failure must not kill the poller; the next tick retries.
                System.err.println("[StatePoller] tick failed: " + e.getMessage());
            }
        }
    }

    public void stop() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }
}
