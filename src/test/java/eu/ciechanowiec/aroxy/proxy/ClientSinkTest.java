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
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class ClientSinkTest {

    private static final String PAYLOAD = "event: message";

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
    void staysUnbrokenWhenOnlyTheUpstreamEndWouldFail() {
        try (ClientSink sink = new ClientSink(new ByteArrayOutputStream())) {
            sink.write(PAYLOAD.getBytes(StandardCharsets.UTF_8));
            assertFalse(sink.isBroken());
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

    private static final class CountingClose extends OutputStream {

        private int closes;

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
