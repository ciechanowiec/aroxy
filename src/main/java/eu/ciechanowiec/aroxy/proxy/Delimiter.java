package eu.ciechanowiec.aroxy.proxy;

/**
 * A blank-line SSE delimiter located in a byte buffer: {@code contentEnd} is the index of the first
 * newline byte (where the event content stops) and {@code skip} is the number of bytes the delimiter
 * spans - 2 for {@code \n\n}, 4 for {@code \r\n\r\n}. {@link #next()} is where the following event
 * begins.
 */
record Delimiter(int contentEnd, int skip) {

    int next() {
        return this.contentEnd + this.skip;
    }
}
