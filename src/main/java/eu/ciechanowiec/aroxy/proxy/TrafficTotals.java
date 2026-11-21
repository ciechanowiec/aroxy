package eu.ciechanowiec.aroxy.proxy;

/**
 * What the proxy has carried since the daemon started, summed over every agent: a point-in-time copy
 * of {@link TrafficStats} that is safe to publish and compare.
 *
 * @param requests          the in-scope exchanges, one per audit stamp
 * @param redacted          the exchanges in which at least one rule matched
 * @param replaced          the matches replaced by a token, counted per occurrence
 * @param sentBytes         the bytes of the redacted requests sent upstream
 * @param receivedBytes     the bytes of the raw responses received from upstream
 * @param lastRequestMillis when the latest exchange began, in epoch milliseconds, or {@code 0} when none has
 */
public record TrafficTotals(
    long requests, long redacted, long replaced, long sentBytes, long receivedBytes, long lastRequestMillis
) {

    /**
     * The totals of a daemon that has carried nothing yet.
     */
    public static final TrafficTotals NONE = new TrafficTotals(0L, 0L, 0L, 0L, 0L, 0L);
}
