package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.Justification;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class ClientSinkTest {

    private static final String PAYLOAD = "event: message";
    private static final int PULSE_MILLIS = 20;
    private static final int AWAIT_SECONDS = 5;

    @Test
    @SneakyThrows
    void passesBytesThroughAndStaysUnbroken() {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        try (ClientSink sink = new ClientSink(target)) {
            sink.write(PAYLOAD.getBytes(StandardCharsets.UTF_8));
            sink.flush();
            assertFalse(sink.isBroken());
        }
        assertEquals(PAYLOAD, target.toString(StandardCharsets.UTF_8));
    }

    @Test
    @SneakyThrows
    void recordsTheClientEndWhenABlockWriteFails() {
        try (ClientSink sink = new ClientSink(new BrokenPipe())) {
            assertThrows(IOException.class, () -> sink.write(PAYLOAD.getBytes(StandardCharsets.UTF_8)));
            assertTrue(sink.isBroken());
        }
    }

    @Test
    @SneakyThrows
    void recordsTheClientEndWhenASingleByteWriteFails() {
        try (ClientSink sink = new ClientSink(new BrokenPipe())) {
            assertThrows(IOException.class, () -> sink.write(1));
            assertTrue(sink.isBroken());
        }
    }

    @Test
    @SneakyThrows
    void recordsTheClientEndWhenAFlushFails() {
        try (ClientSink sink = new ClientSink(new BrokenPipe())) {
            assertThrows(IOException.class, sink::flush);
            assertTrue(sink.isBroken());
        }
    }

    @Test
    @SneakyThrows
    void closesTheStreamItWraps() {
        CountingClose target = new CountingClose();
        try (ClientSink sink = new ClientSink(target)) {
            assertFalse(sink.isBroken());
        }
        assertEquals(1, target.closes());
    }

    @Test
    @SneakyThrows
    void pulsesUntilTheStreamIsClosed() {
        ClientSink sink = new ClientSink(new ByteArrayOutputStream());
        assertTrue(sink.pulsing(), "an open stream is worth keeping warm");
        sink.close();
        assertFalse(sink.pulsing(), "a finished stream needs no keep-alive");
    }

    @Test
    @SneakyThrows
    void stopsPulsingOnceTheClientIsGone() {
        try (ClientSink sink = new ClientSink(new BrokenPipe())) {
            assertThrows(IOException.class, () -> sink.write(1));
            assertFalse(sink.pulsing(), "a stream with no reader left needs no keep-alive");
        }
    }

    @Test
    @SneakyThrows
    void countsOnlyAWholeIntervalWithoutAWriteAsSilence() {
        Duration interval = Duration.ofSeconds(AWAIT_SECONDS);
        try (ClientSink sink = new ClientSink(new ByteArrayOutputStream())) {
            sink.write(PAYLOAD.getBytes(StandardCharsets.UTF_8));
            long written = System.nanoTime();
            assertFalse(sink.silent(written, interval), "a stream that just carried data is not silent");
            assertTrue(
                sink.silent(written + interval.toNanos(), interval),
                "a stream that carried nothing for an interval is silent"
            );
        }
    }

    @Test
    @SneakyThrows
    void datesSilenceFromTheLastWriteRatherThanFromTheStart() {
        Duration interval = Duration.ofSeconds(AWAIT_SECONDS);
        try (ClientSink sink = new ClientSink(new ByteArrayOutputStream())) {
            long opened = System.nanoTime();
            sink.write(PAYLOAD.getBytes(StandardCharsets.UTF_8));
            assertFalse(
                sink.silent(opened + interval.toNanos(), interval),
                "the write pushed the silence deadline out past an interval from the open"
            );
        }
    }

    @Test
    @SneakyThrows
    void flushesEveryKeepAliveSoItLeavesTheProxy() {
        SignallingBytes target = new SignallingBytes();
        try (ClientSink sink = ClientSink.keepingAlive(target, Duration.ofMillis(PULSE_MILLIS))) {
            assertTrue(target.awaitFlush(), "a keep-alive held in a buffer never arrives");
            assertTrue(sink.keepAlives() >= 1, "the keep-alive is counted");
        }
    }

    @Test
    @SneakyThrows
    void writesAKeepAliveCommentWhileTheUpstreamIsSilent() {
        SignallingBytes target = new SignallingBytes();
        try (ClientSink sink = ClientSink.keepingAlive(target, Duration.ofMillis(PULSE_MILLIS))) {
            assertTrue(
                target.awaitWrite(), "a silent stream is kept warm within the timeout"
            );
            assertTrue(sink.keepAlives() >= 1, "the keep-alive is counted");
        }
        assertTrue(target.text().startsWith(": "), target.text());
        assertTrue(target.text().endsWith("\n\n"), "a keep-alive ends on an event boundary");
    }

    private static final class SignallingBytes extends OutputStream {

        private final ByteArrayOutputStream bytes;
        private final CountDownLatch written;
        private final CountDownLatch flushed;

        SignallingBytes() {
            this.bytes = new ByteArrayOutputStream();
            this.written = new CountDownLatch(1);
            this.flushed = new CountDownLatch(1);
        }

        @SneakyThrows
        boolean awaitWrite() {
            return this.written.await(AWAIT_SECONDS, TimeUnit.SECONDS);
        }

        @SneakyThrows
        boolean awaitFlush() {
            return this.flushed.await(AWAIT_SECONDS, TimeUnit.SECONDS);
        }

        @Override
        public void flush() {
            this.flushed.countDown();
        }

        String text() {
            return this.bytes.toString(StandardCharsets.UTF_8);
        }

        @Override
        public void write(int value) {
            this.bytes.write(value);
            this.written.countDown();
        }

        @Justification(
            "Qodana reads the inherited array parameter as non-null and wants the override to restate "
                + "that, while the project declares no nullability annotations to restate it with"
        )
        @SuppressWarnings("NullableProblems")
        @Override
        public void write(byte[] chunk, int offset, int length) {
            this.bytes.write(chunk, offset, length);
            this.written.countDown();
        }
    }

    private static final class CountingClose extends OutputStream {

        private int closes;

        CountingClose() {
            this.closes = 0;
        }

        int closes() {
            return this.closes;
        }

        @Override
        public void write(int value) {
            // A counting stub needs no sink for the byte.
        }

        @Override
        public void close() {
            this.closes += 1;
        }
    }

    private static final class BrokenPipe extends OutputStream {

        @Override
        @SneakyThrows
        public void write(int value) {
            throw new IOException("Broken pipe");
        }

        @Justification(
            "Qodana reads the inherited array parameter as non-null and wants the override to restate "
                + "that, while the project declares no nullability annotations to restate it with"
        )
        @SuppressWarnings("NullableProblems")
        @Override
        @SneakyThrows
        public void write(byte[] chunk, int offset, int length) {
            throw new IOException("Broken pipe");
        }

        @Override
        @SneakyThrows
        public void flush() {
            throw new IOException("Broken pipe");
        }
    }
}
