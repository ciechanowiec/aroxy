package eu.ciechanowiec.aroxy.client;

/**
 * How one Server-Sent-Events block must be handled when reversing a stream. Which kind a block is
 * depends on the client's {@link SseDialect}, not on the block alone.
 */
public enum SseKind {

    /**
     * A block carrying a piece of streamed model text in a known field. The value is reversed through
     * the carry buffer, because a token can be split across two consecutive fragments.
     */
    FRAGMENT,

    /**
     * A block carrying a complete, authoritative copy of what the fragments streamed. It arrives whole,
     * so it needs no carry buffer: the entire block is reversed in one pass.
     *
     * <p>This kind exists because some clients persist the whole-block copy into their conversation
     * history and echo it back on the next request. Reversing only the fragments would leave tokens in
     * that history, which would then egress again.
     */
    WHOLE,

    /**
     * Any other block. It is forwarded unchanged, though a terminal one first flushes a held partial.
     */
    PLAIN
}
