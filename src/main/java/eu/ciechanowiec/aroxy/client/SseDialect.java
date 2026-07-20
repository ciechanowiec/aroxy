package eu.ciechanowiec.aroxy.client;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The shape of one client's Server-Sent-Events stream, held as data rather than as code, so that
 * supporting another client means writing another value of this type rather than another subclass. The
 * proxy interprets a dialect; it does not know any vendor's vocabulary itself.
 *
 * <p>A block is classified as one {@link SseKind}: a {@link FragmentRule} match makes it a
 * {@link SseKind#FRAGMENT}, a {@link #wholeMarkers} match makes it a {@link SseKind#WHOLE}, and
 * anything else is {@link SseKind#PLAIN}. Fragments are tried first, because a client whose whole-block
 * marker is a prefix of a fragment marker would otherwise lose the carry buffer.
 *
 * <p>{@link #terminalMarkers} name the blocks that must flush a held partial token before they are
 * forwarded. A {@link SseKind#WHOLE} block always flushes and so need not be listed here.
 *
 * @param fragments       the ways this client streams model text, in the order they are tried
 * @param wholeMarkers    the text identifying a block that carries a complete copy of the streamed text
 * @param terminalMarkers the text identifying a block before which a held partial token must be flushed
 */
public record SseDialect(
    List<FragmentRule> fragments,
    Set<String> wholeMarkers,
    Set<String> terminalMarkers
) {

    // copied so the dialect cannot be mutated through the collections it was built from
    public SseDialect {
        fragments = List.copyOf(fragments);
        wholeMarkers = Set.copyOf(wholeMarkers);
        terminalMarkers = Set.copyOf(terminalMarkers);
    }

    /**
     * Classifies one event block.
     *
     * @param block the text of one Server-Sent-Events block
     * @return how that block must be handled
     */
    public SseKind classify(String block) {
        if (this.fragment(block).isPresent()) {
            return SseKind.FRAGMENT;
        }
        return contains(block, this.wholeMarkers) ? SseKind.WHOLE : SseKind.PLAIN;
    }

    /**
     * The rule matching this block, naming the key whose value carries the streamed text.
     *
     * @param block the text of one Server-Sent-Events block
     * @return the matching rule, or empty when the block streams no text
     */
    public Optional<FragmentRule> fragment(String block) {
        return this.fragments.stream().filter(rule -> rule.matches(block)).findFirst();
    }

    /**
     * Whether a held partial token must be flushed before this block is forwarded.
     *
     * @param block the text of one Server-Sent-Events block
     * @return whether the block ends a run of fragments
     */
    public boolean isTerminal(String block) {
        return contains(block, this.terminalMarkers);
    }

    private static boolean contains(String block, Collection<String> markers) {
        return markers.stream().anyMatch(block::contains);
    }
}
