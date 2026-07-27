package eu.ciechanowiec.aroxy.proxy;

/**
 * The per-request side of one Cursor Connect exchange, as seen by the response reader: where to report
 * the reversal count once the logical turn ends, and where to hand the raw upstream envelopes for the
 * wire dump. It is one object per request, so a shared listener never mixes two streams together.
 */
interface CursorTurn {

    /**
     * Reports that the logical turn ended.
     *
     * @param reversed how many tokens were restored in it
     */
    void completed(int reversed);

    /**
     * Offers one raw upstream envelope for the wire dump.
     *
     * @param frame the untransformed envelope, exactly as the upstream sent it
     */
    void received(byte[] frame);
}
