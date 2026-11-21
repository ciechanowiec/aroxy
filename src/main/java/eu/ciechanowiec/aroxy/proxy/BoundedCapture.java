package eu.ciechanowiec.aroxy.proxy;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import lombok.extern.slf4j.Slf4j;

/**
 * Collects both directions of one streamed exchange for its wire dumps, up to a cap per direction. A
 * streamed exchange has no single body and no bounded length, so the capture is truncated rather than
 * allowed to grow with the turn: a dump is a diagnostic, and holding an entire agent session in memory
 * to produce one would be a worse failure than an incomplete dump.
 *
 * <p>An envelope is taken whole or not at all, so the cap is a cap rather than a threshold one envelope
 * may overrun, and what is written stays a sequence of complete envelopes rather than a stream ending
 * mid-frame. Truncation is reported once per direction, because a dump that is quietly short reads
 * exactly like a turn that was quietly short, and a diagnostic that can mislead about its own
 * completeness is worse than no diagnostic.
 *
 * <p>Each direction also counts every byte offered to it, kept or refused, so the traffic reported by
 * {@code aroxy status} is the stream's true size rather than the size of its capped dump.
 *
 * <p>The two directions travel together because they are dumped together, under one audit stamp.
 */
@Slf4j
final class BoundedCapture {

    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private final Direction sent;
    private final Direction received;

    BoundedCapture() {
        this.sent = new Direction("request");
        this.received = new Direction("response");
    }

    void sent(byte[] frame) {
        this.sent.append(frame);
    }

    void received(byte[] frame) {
        this.received.append(frame);
    }

    byte[] sentBytes() {
        return this.sent.bytes();
    }

    byte[] receivedBytes() {
        return this.received.bytes();
    }

    long sentTotal() {
        return this.sent.total();
    }

    long receivedTotal() {
        return this.received.total();
    }

    /**
     * One direction's capture: the envelopes kept, and whether the cap has already been announced.
     */
    private static final class Direction {

        private final String label;
        private final ByteArrayOutputStream kept;
        private final AtomicBoolean announced;
        private final LongAdder offered;

        Direction(String label) {
            this.label = label;
            this.kept = new ByteArrayOutputStream();
            this.announced = new AtomicBoolean();
            this.offered = new LongAdder();
        }

        void append(byte[] frame) {
            this.offered.add(frame.length);
            if (this.kept.size() + frame.length <= MAX_BYTES) {
                this.kept.writeBytes(frame);
            } else {
                this.refuse();
            }
        }

        byte[] bytes() {
            return this.kept.toByteArray();
        }

        long total() {
            return this.offered.sum();
        }

        // A flag rather than a running total of the bytes refused. What a refusal is worth saying is that
        // the dump is short, which is said once and does not need a number. The bytes offered are counted
        // separately, for the traffic totals, and need no help from here.
        //
        // Announced on the first refusal only: a capped turn refuses every envelope that follows, and one
        // line per envelope would bury the diagnostic log under the very stream it is warning about.
        private void refuse() {
            if (this.announced.compareAndSet(false, true)) {
                log.info(
                    "The Cursor {} wire dump reached its {}-byte cap and is truncated", this.label, MAX_BYTES
                );
            }
        }
    }
}
