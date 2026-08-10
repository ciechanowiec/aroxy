package eu.ciechanowiec.aroxy.obfuscation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;

/**
 * The character ranges already occupied by {@code <<PII:...>>} tokens in input text. Protecting those
 * ranges keeps a rule (for example a bare {@code \d+} pattern) from matching inside a token and
 * corrupting it, which would make the value impossible to reverse on the response.
 */
final class TokenRegions {

    private final List<int[]> ranges;

    private TokenRegions(List<int[]> ranges) {
        this.ranges = ranges;
    }

    /**
     * Records every existing token span in {@code text}.
     */
    static TokenRegions scan(CharSequence text) {
        List<int[]> ranges = new ArrayList<>();
        Matcher matcher = PiiToken.TOKEN.matcher(text);
        while (matcher.find()) {
            ranges.add(new int[] {matcher.start(), matcher.end()});
        }
        return new TokenRegions(ranges);
    }

    /**
     * The largest token-free sub-range of the half-open match {@code [start, end)}: the match trimmed
     * so it no longer overlaps any existing token. Empty when the match lies inside a token, or still
     * straddles one after trimming, so the caller leaves the match verbatim rather than corrupt a
     * token. Trimming the overlap (instead of skipping the whole match) keeps the sensitive part that
     * lies outside the token from leaking unobfuscated.
     */
    Optional<int[]> tokenFreeSpan(int start, int end) {
        int from = this.trimStart(start, end);
        int to = this.trimEnd(from, end);
        return this.cleanWindow(from, to);
    }

    private int trimStart(int start, int end) {
        return this.ranges.stream()
            .filter(range -> range[0] <= start && start < range[1])
            .mapToInt(range -> Math.min(range[1], end))
            .findFirst().orElse(start);
    }

    private int trimEnd(int start, int end) {
        return this.ranges.stream()
            .filter(range -> range[0] < end && end <= range[1])
            .mapToInt(range -> Math.max(range[0], start))
            .findFirst().orElse(end);
    }

    private Optional<int[]> cleanWindow(int start, int end) {
        boolean clean = start < end && this.ranges.stream().noneMatch(range -> overlaps(range, start, end));
        return clean ? Optional.of(new int[] {start, end}) : Optional.empty();
    }

    private static boolean overlaps(int[] range, int start, int end) {
        return start < range[1] && end > range[0];
    }
}
