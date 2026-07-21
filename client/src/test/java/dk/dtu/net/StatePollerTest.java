package dk.dtu.net;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavior of the polling loop that replaced the jSpace notification listener.
 */
public class StatePollerTest {

    @Test
    void tickFiresWhenShouldPollIsTrue() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        StatePoller poller = new StatePoller(20, latch::countDown, () -> true);
        Thread t = new Thread(poller, "test-poller");
        t.setDaemon(true);
        t.start();

        assertTrue(latch.await(2, TimeUnit.SECONDS), "poller should tick repeatedly");
        poller.stop();
        t.interrupt();
    }

    @Test
    void tickIsSkippedWhenShouldPollIsFalse() throws Exception {
        AtomicInteger ticks = new AtomicInteger();
        StatePoller poller = new StatePoller(20, ticks::incrementAndGet, () -> false);
        Thread t = new Thread(poller, "test-poller-paused");
        t.setDaemon(true);
        t.start();

        Thread.sleep(150);
        poller.stop();
        t.interrupt();

        assertEquals(0, ticks.get(), "no ticks should fire while paused");
    }

    @Test
    void stopHaltsTheLoop() throws Exception {
        AtomicInteger ticks = new AtomicInteger();
        StatePoller poller = new StatePoller(20, ticks::incrementAndGet, () -> true);
        Thread t = new Thread(poller, "test-poller-stop");
        t.setDaemon(true);
        t.start();

        Thread.sleep(100);
        poller.stop();
        t.interrupt();
        t.join(1000);

        int afterStop = ticks.get();
        Thread.sleep(100);
        assertEquals(afterStop, ticks.get(), "no further ticks after stop");
        assertFalse(poller.isRunning());
    }
}
