package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * Proves that Cursor response auditing follows the terminal Connect envelope rather than input EOF.
 */
class CursorResponseAuditTimingTest {

    private static final String TOKEN = "<<PII:AWS_KEY:1>>";
    private static final Duration WAIT = Duration.ofSeconds(5);

    @Test
    @SneakyThrows
    void reportsResponseAtConnectEndStreamBeforeInputEof() {
        CursorConnectResponse response = CursorWire.responseTransformer();
        try (
            PipedInputStream input = new PipedInputStream();
            PipedOutputStream upstream = new PipedOutputStream(input)
        ) {
            ResponseState state = new ResponseState(
                new CapturingSink(), new CountDownLatch(1), new AtomicInteger()
            );
            FutureTask<Boolean> reader = reader(response, input, state);
            Thread.startVirtualThread(reader);
            upstream.write(response());
            upstream.flush();
            assertTrue(
                state.reported().await(WAIT.toSeconds(), TimeUnit.SECONDS),
                "the response summary is emitted at the terminal Connect envelope"
            );
            assertEquals(1, state.reversed().get());
            assertFalse(reader.isDone(), "the response reader is still waiting for input EOF");
            releaseEof(upstream);
            reader.get(WAIT.toSeconds(), TimeUnit.SECONDS);
            assertTrue(
                CursorWire.text(state.client().bytes()).contains(ProxyHarness.SECRET),
                "the response was restored while it streamed"
            );
        }
    }

    @SneakyThrows
    private static void releaseEof(PipedOutputStream upstream) {
        upstream.close();
    }

    private static byte[] response() {
        return CursorWire.concat(
            CursorWire.frame(CursorWire.protobuf(CursorWire.protobuf(TOKEN))),
            CursorWire.framed(ConnectJsonFrame.END_STREAM, "{}")
        );
    }

    private static FutureTask<Boolean> reader(
        CursorConnectResponse response, PipedInputStream input, ResponseState state
    ) {
        return new FutureTask<>(
            () -> {
                response.transform(
                    state.client(), input, CursorHttp.WireFormat.PROTOBUF, turn(state)
                );
                return true;
            }
        );
    }

    // This test only watches when the reversal count is reported, so the raw envelopes it is offered
    // for the wire dump are discarded.
    private static CursorTurn turn(ResponseState state) {
        return new CursorTurn() {

            @Override
            public void completed(int reversed) {
                state.reversed().set(reversed);
                state.reported().countDown();
            }

            @Override
            public void received(byte[] frame) {
                // no dump is collected in this test
            }
        };
    }

    private record ResponseState(
        CapturingSink client, CountDownLatch reported, AtomicInteger reversed
    ) {
    }
}
