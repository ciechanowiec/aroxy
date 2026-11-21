package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.Justification;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * The Connect envelope reader and writer, and the header filter beside them. An envelope declares its
 * own payload length, so the reader has to distinguish a payload that is too large to accept from one
 * that is merely absent, and the writer has to push each envelope out rather than leave it buffered
 * behind a stream that may stay open for the rest of the turn.
 */
@Justification(
    "in-memory fixtures: the sink has nothing to release"
)
@SuppressWarnings(
    "PMD.CloseResource"
)
class ConnectFramingTest {

    private static final int MAX_PAYLOAD = 64 * 1024 * 1024;

    // A declared length at the cap is legal, so reaching it must not be reported as exceeding it. The
    // two are told apart by which failure the reader raises when the payload does not arrive.
    @Test
    @SneakyThrows
    void acceptsALengthAtTheCapAndRejectsOneBeyondIt() {
        ConnectProtocolException atCap = assertThrows(
            ConnectProtocolException.class, () -> ConnectJsonFrame.read(header(MAX_PAYLOAD))
        );
        String atCapMessage = String.valueOf(atCap.getMessage());
        assertTrue(
            atCapMessage.contains("Truncated"),
            "a legal length is read, and only then found to be unfulfilled: " + atCapMessage
        );
        ConnectProtocolException beyondCap = assertThrows(
            ConnectProtocolException.class, () -> ConnectJsonFrame.read(header(MAX_PAYLOAD + 1))
        );
        String beyondCapMessage = String.valueOf(beyondCap.getMessage());
        assertTrue(
            beyondCapMessage.contains("exceeds"),
            "one byte past the cap is refused outright: " + beyondCapMessage
        );
    }

    @Test
    @SneakyThrows
    void pushesEachEnvelopeOutRatherThanLeavingItBuffered() {
        Flushes sink = new Flushes();
        new ConnectJsonFrame(0, "{}".getBytes(StandardCharsets.UTF_8)).write(sink);
        assertTrue(sink.count() > 0, "a written envelope reaches the socket without waiting for more");
    }

    @Test
    void dropsHttpTwoPseudoHeadersAndKeepsOrdinaryOnes() {
        Map<String, List<String>> copied = new HashMap<>();
        ResponseHeaderCopier.copy(
            Map.of(":status", List.of("200"), "x-keep", List.of("yes")), copied
        );
        assertEquals(Map.of("x-keep", List.of("yes")), copied, "a pseudo-header is never copied on");
    }

    @SneakyThrows
    private static ByteArrayInputStream header(long declaredLength) {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        head.write(0);
        head.write(ByteBuffer.allocate(Integer.BYTES).putInt((int) declaredLength).array());
        return new ByteArrayInputStream(head.toByteArray());
    }

    /**
     * Counts flushes, because "the agent sees this envelope now" is a property of the flush rather than
     * of the bytes written.
     */
    private static final class Flushes extends OutputStream {

        private final AtomicInteger count;

        Flushes() {
            this.count = new AtomicInteger();
        }

        @Override
        public void write(int value) {
            // the bytes are irrelevant here, and only the flush is under test
        }

        @Override
        public void flush() {
            this.count.incrementAndGet();
        }

        int count() {
            return this.count.get();
        }
    }
}
