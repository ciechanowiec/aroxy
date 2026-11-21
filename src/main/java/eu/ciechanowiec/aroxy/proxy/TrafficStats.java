package eu.ciechanowiec.aroxy.proxy;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * The live traffic counters behind {@code aroxy status}. Every listener records into one instance from
 * its own request threads, so each counter is a {@link LongAdder}: increments never contend, and a
 * {@link #totals()} read taken mid-request is at worst one exchange behind.
 *
 * <p>The counters sit beside the {@link AuditLog} that owns them because the audit trail is already
 * the one place every agent's exchange, matches, and wire bodies pass through.
 */
final class TrafficStats {

    private final LongAdder requests;
    private final LongAdder redacted;
    private final LongAdder replaced;
    private final LongAdder sent;
    private final LongAdder received;
    private final AtomicLong lastRequestMillis;

    TrafficStats() {
        this.requests = new LongAdder();
        this.redacted = new LongAdder();
        this.replaced = new LongAdder();
        this.sent = new LongAdder();
        this.received = new LongAdder();
        this.lastRequestMillis = new AtomicLong();
    }

    /**
     * Records that an in-scope exchange began now.
     */
    void exchanged() {
        this.requests.increment();
        this.lastRequestMillis.set(System.currentTimeMillis());
    }

    /**
     * Records the matches replaced in one request body or frame.
     *
     * @param replacements  the number of matches replaced by a token
     * @param newlyRedacted whether these matches are the first of their exchange, so it now counts as redacted
     */
    void matched(int replacements, boolean newlyRedacted) {
        this.replaced.add(replacements);
        if (newlyRedacted) {
            this.redacted.increment();
        }
    }

    void sent(long bytes) {
        this.sent.add(bytes);
    }

    void received(long bytes) {
        this.received.add(bytes);
    }

    TrafficTotals totals() {
        return new TrafficTotals(
            this.requests.sum(), this.redacted.sum(), this.replaced.sum(),
            this.sent.sum(), this.received.sum(), this.lastRequestMillis.get()
        );
    }
}
