package dk.dtu;

import javafx.application.Platform;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Redirects System.out/System.err to a UI callback (line-by-line).
 */
public final class UiConsoleRedirect implements AutoCloseable {

    public interface LineConsumer {
        void accept(String line);
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final PrintStream originalOut;
    private final PrintStream originalErr;

    private final PrintStream uiOut;
    private final PrintStream uiErr;

    public UiConsoleRedirect(LineConsumer consumer) {
        Objects.requireNonNull(consumer);

        this.originalOut = System.out;
        this.originalErr = System.err;

        OutputStream teeOut = new TeeOutputStream(
            new PrintStreamOutputStream(originalOut),
            new LineBufferingOutputStream(false, consumer)
        );
        OutputStream teeErr = new TeeOutputStream(
            new PrintStreamOutputStream(originalErr),
            new LineBufferingOutputStream(true, consumer)
        );

        this.uiOut = new PrintStream(teeOut, true, StandardCharsets.UTF_8);
        this.uiErr = new PrintStream(teeErr, true, StandardCharsets.UTF_8);

        System.setOut(uiOut);
        System.setErr(uiErr);
    }

    @Override
    public void close() {
        try {
            System.setOut(originalOut);
        } catch (Exception ignored) {
        }
        try {
            System.setErr(originalErr);
        } catch (Exception ignored) {
        }

        try {
            uiOut.flush();
        } catch (Exception ignored) {
        }
        try {
            uiErr.flush();
        } catch (Exception ignored) {
        }
    }

    private static final class LineBufferingOutputStream extends OutputStream {
        private final boolean isErr;
        private final LineConsumer consumer;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(256);

        private LineBufferingOutputStream(boolean isErr, LineConsumer consumer) {
            this.isErr = isErr;
            this.consumer = consumer;
        }

        @Override
        public synchronized void write(int b) {
            if (b == '\n') {
                flushLine();
                return;
            }
            if (b != '\r') {
                buffer.write(b);
            }
        }

        @Override
        public synchronized void flush() {
            flushLine();
        }

        private void flushLine() {
            if (buffer.size() == 0) {
                return;
            }

            String msg = buffer.toString(StandardCharsets.UTF_8);
            buffer.reset();

            String prefix = LocalTime.now().format(TIME_FMT) + " " + (isErr ? "[ERR] " : "[OUT] ");
            String line = prefix + msg;

            Platform.runLater(() -> consumer.accept(line));
        }
    }

    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream a;
        private final OutputStream b;

        private TeeOutputStream(OutputStream a, OutputStream b) {
            this.a = Objects.requireNonNull(a);
            this.b = Objects.requireNonNull(b);
        }

        @Override
        public synchronized void write(int bByte) {
            try {
                a.write(bByte);
            } catch (Exception ignored) {
            }
            try {
                b.write(bByte);
            } catch (Exception ignored) {
            }
        }

        @Override
        public synchronized void write(byte[] bytes, int off, int len) {
            try {
                a.write(bytes, off, len);
            } catch (Exception ignored) {
            }
            try {
                b.write(bytes, off, len);
            } catch (Exception ignored) {
            }
        }

        @Override
        public synchronized void flush() {
            try {
                a.flush();
            } catch (Exception ignored) {
            }
            try {
                b.flush();
            } catch (Exception ignored) {
            }
        }
    }

    private static final class PrintStreamOutputStream extends OutputStream {
        private final PrintStream target;

        private PrintStreamOutputStream(PrintStream target) {
            this.target = Objects.requireNonNull(target);
        }

        @Override
        public void write(int b) {
            target.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            target.write(b, off, len);
        }

        @Override
        public void flush() {
            target.flush();
        }
    }
}
