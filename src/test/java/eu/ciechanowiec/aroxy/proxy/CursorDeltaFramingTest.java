package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.proxy.CursorHttp.WireFormat;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * Reads the restored protobuf reply frame by frame and field by field, rather than scanning it for a
 * substring. Two of its properties are invisible to a scan: where the replayed remainder sits in the
 * stream, and where it sits inside the frame. A remainder written after the end-of-stream frame, or one
 * nesting level out from the fragment field, still puts the same characters somewhere in the same bytes,
 * so a scan passes either way while an agent reading the stream properly sees neither.
 */
class CursorDeltaFramingTest {

    private static final List<Integer> FRAGMENT_PATH = List.of(1, 1, 1);
    private static final String SENT = "trailing <<PII:AWS_";
    private static final String EMITTED = "trailing ";
    private static final String HELD = "<<PII:AWS_";
    private static final String TOKEN = "<<PII:AWS_KEY:1>>";

    // The remainder is flushed ahead of the end-of-stream frame on purpose: an agent that stops reading
    // at the terminal frame would otherwise lose the tail of the last delta. Only the order proves it.
    @Test
    void replaysTheHeldRemainderBeforeTheEndOfStreamFrame() {
        List<ConnectJsonFrame> frames = restored(
            CursorWire.concat(
                CursorWire.frame(CursorWire.textDelta(SENT)),
                CursorWire.framed(ConnectJsonFrame.END_STREAM, new byte[0])
            )
        );
        assertEquals(3, frames.size(), "the delta, the replay of what it held, and the end of stream");
        assertFalse(frames.getFirst().terminal(), "the delta comes first");
        assertFalse(frames.get(1).terminal(), "then the replay, while the stream is still open");
        assertTrue(frames.get(2).terminal(), "and the end-of-stream frame closes it");
    }

    @Test
    void emitsOnlyTheSafePrefixInTheDeltaItself() {
        List<ConnectJsonFrame> frames = restored(
            CursorWire.concat(
                CursorWire.frame(CursorWire.textDelta(SENT)),
                CursorWire.framed(ConnectJsonFrame.END_STREAM, new byte[0])
            )
        );
        assertEquals(EMITTED, fragmentAt(frames.getFirst()), "the partial token is held back");
    }

    // The replay is the same frame with one field rewritten, so the remainder has to land on the field
    // Cursor reads its text from. One level out it is still in the bytes and still invisible to the CLI.
    @Test
    void putsTheReplayedRemainderOnTheFragmentField() {
        List<ConnectJsonFrame> frames = restored(
            CursorWire.concat(
                CursorWire.frame(CursorWire.textDelta(SENT)),
                CursorWire.framed(ConnectJsonFrame.END_STREAM, new byte[0])
            )
        );
        assertEquals(HELD, fragmentAt(frames.get(1)), "the remainder sits where the fragment does");
    }

    @Test
    void restoresASplitTokenOntoTheFragmentFieldOfTheCompletingFrame() {
        List<ConnectJsonFrame> frames = restored(
            CursorWire.concat(
                CursorWire.frame(CursorWire.textDelta("Ask <<PII:AWS_")),
                CursorWire.concat(
                    CursorWire.frame(CursorWire.textDelta("KEY:1>> now")),
                    CursorWire.framed(ConnectJsonFrame.END_STREAM, new byte[0])
                )
            )
        );
        assertEquals("Ask ", fragmentAt(frames.getFirst()), "nothing is emitted until the token completes");
        assertEquals(
            ProxyHarness.SECRET + " now", fragmentAt(frames.get(1)),
            "the completing frame carries the restored value on the fragment field"
        );
    }

    // A fragment whose own bytes parse as a well-formed message is what the fragment path is declared
    // text for. Without that declaration the transformer prefers the message reading, the fragment never
    // reaches the carry buffer, and the partial token at its end goes straight to the agent unheld.
    @Test
    void holdsAPartialEvenWhenTheFragmentAlsoParsesAsAMessage() {
        String fragment = CursorWire.text(
            CursorWire.protobuf(TOKEN + " tail " + HELD)
        );
        List<ConnectJsonFrame> frames = restored(
            CursorWire.concat(
                CursorWire.frame(CursorWire.textDelta(fragment)),
                CursorWire.framed(ConnectJsonFrame.END_STREAM, new byte[0])
            )
        );
        assertEquals(3, frames.size(), "the delta, the replay of what it held, and the end of stream");
        assertFalse(
            fragmentAt(frames.getFirst()).contains(HELD),
            "the partial token is held rather than emitted with the fragment"
        );
        assertEquals(HELD, fragmentAt(frames.get(1)), "and is replayed on the fragment field");
    }

    // One counter covers both kinds of leaf, so a stream carrying a token in a fragment and another in
    // a whole value beside it reports their sum rather than either alone.
    @Test
    void reportsFragmentAndWholeReversalsTogether() {
        RecordingTurn turn = new RecordingTurn();
        transform(
            CursorWire.concat(
                CursorWire.frame(
                    CursorWire.concat(
                        CursorWire.textDelta("Ask " + TOKEN + " now"),
                        CursorWire.protobuf("also " + TOKEN)
                    )
                ),
                CursorWire.framed(ConnectJsonFrame.END_STREAM, new byte[0])
            ),
            turn
        );
        assertEquals(2, turn.reversed(), "one reversal from the fragment run and one from the whole value");
    }

    private static String fragmentAt(ConnectJsonFrame frame) {
        return CursorReply.valueAt(frame, FRAGMENT_PATH);
    }

    private static List<ConnectJsonFrame> restored(byte[] upstream) {
        return CursorReply.frames(transform(upstream, new RecordingTurn()));
    }

    @SneakyThrows
    private static byte[] transform(byte[] upstream, CursorTurn turn) {
        CapturingSink sink = new CapturingSink();
        CursorWire.responseTransformer().transform(
            sink, new ByteArrayInputStream(upstream), WireFormat.PROTOBUF, turn
        );
        return sink.bytes();
    }

    private static final class RecordingTurn implements CursorTurn {

        private final AtomicInteger reversed;

        RecordingTurn() {
            this.reversed = new AtomicInteger();
        }

        @Override
        public void completed(int reversed) {
            this.reversed.set(reversed);
        }

        @Override
        public void received(byte[] frame) {
            // the wire dump is asserted by the audit tests, not by this one
        }

        int reversed() {
            return this.reversed.get();
        }
    }
}
