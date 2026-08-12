package eu.ciechanowiec.aroxy.proxy;

/**
 * How a {@link FakeUpstream} reply ends. A proxy behaves differently on a stream that finishes, one
 * that stays open, and one that dies in flight, and only the last of those exercises the paths that
 * handle a broken response.
 */
enum ReplyEnding {

    /**
     * The whole body is written with a declared length and the exchange succeeds.
     */
    COMPLETE,

    /**
     * The body is written as a non-final chunk and the stream is held open until it is released.
     */
    HELD_OPEN,

    /**
     * The body is written as a non-final chunk and the exchange then fails, so the connection dies
     * with the response committed and no terminating chunk.
     */
    TRUNCATED
}
