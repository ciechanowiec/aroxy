package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A wire dump of a streamed exchange is truncated rather than allowed to grow with the turn, so a long
 * agent session cannot be held whole in memory just to produce a diagnostic. The cap is a cap: an
 * envelope is taken whole or refused, so no single envelope can carry the capture past it, and what is
 * kept stays a sequence of complete envelopes rather than a stream cut mid-frame. The two directions
 * are capped independently, so a talkative upstream cannot crowd out the record of what was sent.
 */
class BoundedCaptureTest {

    private static final int CHUNK = 3 * 1024 * 1024;
    private static final int CAP = 4 * 1024 * 1024;

    @Test
    void collectsEnvelopesUntilOneWouldCrossTheCap() {
        BoundedCapture capture = new BoundedCapture();
        capture.sent(new byte[CHUNK]);
        assertEquals(CHUNK, capture.sentBytes().length, "everything under the cap is kept");
        capture.sent(new byte[CHUNK]);
        assertEquals(CHUNK, capture.sentBytes().length, "the envelope that would cross the cap is refused whole");
        capture.sent(new byte[CHUNK]);
        assertEquals(CHUNK, capture.sentBytes().length, "nothing is kept once the cap is reached");
    }

    // The dump is capped, but the traffic it reports is not: a long turn's true size is what the
    // status counts, so the totals keep growing after the dump stops.
    @Test
    void totalsCountEveryEnvelopeEvenPastTheCap() {
        BoundedCapture capture = new BoundedCapture();
        capture.sent(new byte[CHUNK]);
        capture.sent(new byte[CHUNK]);
        capture.received(new byte[1]);
        assertEquals(CHUNK, capture.sentBytes().length, "the second envelope is refused by the dump");
        assertEquals(2L * CHUNK, capture.sentTotal(), "but still counted as sent");
        assertEquals(1L, capture.receivedTotal());
    }

    // The cap is a ceiling on the bytes held, not a threshold one envelope may overrun. A 64 MiB
    // envelope is a legal Connect frame, so a check made before the write rather than against the
    // write would let the capture exceed its own limit sixteenfold.
    @Test
    void neverExceedsTheCap() {
        BoundedCapture capture = new BoundedCapture();
        capture.sent(new byte[CAP - 1]);
        capture.sent(new byte[CHUNK]);
        assertTrue(capture.sentBytes().length <= CAP, "the capture never grows past its cap");
        assertEquals(CAP - 1, capture.sentBytes().length, "the refused envelope leaves the capture as it was");
    }

    @Test
    void takesAnEnvelopeThatLandsExactlyOnTheCap() {
        BoundedCapture capture = new BoundedCapture();
        capture.sent(new byte[CAP - 1]);
        capture.sent(new byte[1]);
        assertEquals(CAP, capture.sentBytes().length, "an envelope filling the cap exactly still fits");
    }

    @Test
    void capsEachDirectionOnItsOwn() {
        BoundedCapture capture = new BoundedCapture();
        capture.received(new byte[CHUNK]);
        capture.received(new byte[CHUNK]);
        capture.sent(new byte[1]);
        assertEquals(1, capture.sentBytes().length, "a full inbound capture does not close the outbound one");
        assertEquals(CHUNK, capture.receivedBytes().length, "while the inbound one has refused its second");
    }
}
