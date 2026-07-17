package eu.ciechanowiec.aroxy.obfuscation;

import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * Finds the largest prefix of a text fragment that is safe to emit because it cannot be the start of
 * a token that will only complete in a later fragment. A trailing partial token (either an open
 * {@code <<PII:} with no closing {@code >>}, or a partial of the {@code <<PII:} marker itself) is
 * held back, but never more than {@link #MAX_HOLD} characters, since a real token is short.
 */
@UtilityClass
final class TokenBoundary {

    private static final int MAX_HOLD = 64;

    static int of(String value) {
        return openTokenBoundary(value).orElseGet(() -> partialMarkerBoundary(value));
    }

    private static Optional<Integer> openTokenBoundary(String value) {
        int lastOpen = value.lastIndexOf(PiiToken.OPEN);
        boolean unclosed = lastOpen >= 0
            && value.indexOf(PiiToken.CLOSE, lastOpen + PiiToken.OPEN.length()) < 0;
        if (!unclosed) {
            return Optional.empty();
        }
        return Optional.of(capHold(value.length(), lastOpen));
    }

    private static int partialMarkerBoundary(String value) {
        int maxPrefixLength = Math.min(PiiToken.OPEN.length() - 1, value.length());
        for (int prefixLength = maxPrefixLength; prefixLength > 0; prefixLength--) {
            if (value.regionMatches(value.length() - prefixLength, PiiToken.OPEN, 0, prefixLength)) {
                return capHold(value.length(), value.length() - prefixLength);
            }
        }
        return value.length();
    }

    private static int capHold(int total, int boundary) {
        boolean tooLong = total - boundary > MAX_HOLD;
        return tooLong ? total : boundary;
    }
}
