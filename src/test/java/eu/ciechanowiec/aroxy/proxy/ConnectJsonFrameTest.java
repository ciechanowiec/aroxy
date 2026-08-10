package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * Verifies Cursor Connect envelope framing independently from proxy behavior.
 */
class ConnectJsonFrameTest {

    @Test
    @SneakyThrows
    void roundTripsAcrossArbitraryInputChunks() {
        byte[] encoded = frame("{\"text\":\"hello\"}".getBytes(StandardCharsets.UTF_8));
        ConnectJsonFrame decoded = ConnectJsonFrame.read(new ByteArrayInputStream(encoded)).orElseThrow();
        ByteArrayOutputStream replayed = new ByteArrayOutputStream();
        decoded.write(replayed);
        assertArrayEquals(encoded, replayed.toByteArray(), "the wire envelope is unchanged");
        assertTrue(ConnectJsonFrame.read(new ByteArrayInputStream(new byte[0])).isEmpty());
    }

    @Test
    void rejectsTruncatedAndOversizedEnvelopes() {
        assertThrows(
            ConnectProtocolException.class,
            () -> ConnectJsonFrame.read(new ByteArrayInputStream(new byte[] {0, 0}))
        );
        byte[] missingPayload = {0, 0, 0, 0, 1};
        assertThrows(
            ConnectProtocolException.class,
            () -> ConnectJsonFrame.read(new ByteArrayInputStream(missingPayload))
        );
        byte[] tooLarge = ByteBuffer.allocate(5).put((byte) 0).putInt(Integer.MAX_VALUE).array();
        assertThrows(
            ConnectProtocolException.class,
            () -> ConnectJsonFrame.read(new ByteArrayInputStream(tooLarge))
        );
    }

    @Test
    void exposesEnvelopeFlags() {
        assertTrue(new ConnectJsonFrame(1, new byte[0]).compressed());
        assertTrue(new ConnectJsonFrame(ConnectJsonFrame.END_STREAM, new byte[0]).terminal());
    }

    @SneakyThrows
    private static byte[] frame(byte[] payload) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new ConnectJsonFrame(0, payload).write(output);
        return output.toByteArray();
    }
}
