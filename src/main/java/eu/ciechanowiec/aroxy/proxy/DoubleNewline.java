package eu.ciechanowiec.aroxy.proxy;

import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * Finds the blank-line delimiter that separates Server-Sent-Events in a byte buffer. A blank line is
 * framed either as {@code \n\n} (the form the upstreams emit) or as {@code \r\n\r\n} (the form some
 * HTTP intermediaries rewrite it to). Both are recognised so events drain incrementally either way
 * rather than piling up until the stream ends. The framing is the same whichever agent is served, so
 * this class is shared by every dialect.
 */
@UtilityClass
final class DoubleNewline {

    private static final byte LF = (byte) '\n';
    private static final byte CR = (byte) '\r';
    private static final int LF_SKIP = 2;
    private static final int CRLF_SKIP = 4;

    static Optional<Delimiter> find(byte[] buffer, int from) {
        for (int index = Math.max(from, 0); index < buffer.length - 1; index++) {
            Optional<Delimiter> match = at(buffer, index);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private static Optional<Delimiter> at(byte[] buffer, int index) {
        return lfBreak(buffer, index).or(() -> crlfBreak(buffer, index));
    }

    private static Optional<Delimiter> lfBreak(byte[] buffer, int index) {
        boolean isBreak = buffer[index] == LF && buffer[index + 1] == LF;
        return isBreak ? Optional.of(new Delimiter(index, LF_SKIP)) : Optional.empty();
    }

    private static Optional<Delimiter> crlfBreak(byte[] buffer, int index) {
        boolean isBreak = index + CRLF_SKIP <= buffer.length
            && isCrlf(buffer, index) && isCrlf(buffer, index + LF_SKIP);
        return isBreak ? Optional.of(new Delimiter(index, CRLF_SKIP)) : Optional.empty();
    }

    private static boolean isCrlf(byte[] buffer, int index) {
        return buffer[index] == CR && buffer[index + 1] == LF;
    }
}
