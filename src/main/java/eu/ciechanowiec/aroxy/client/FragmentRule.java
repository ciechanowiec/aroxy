package eu.ciechanowiec.aroxy.client;

import java.util.Set;

/**
 * One way a client's stream carries a piece of model text: the markers that identify such a block and
 * the JSON key whose value holds the text. A block matches only when every marker is present, so a rule
 * can require both an event type and a delta type.
 *
 * <p>Markers and the key are matched as raw text against the still-JSON-escaped block rather than
 * against a parsed document. That is deliberate and matches the rest of the engine: reversing a value
 * without re-serializing the block leaves the vendor's exact escaping and framing untouched.
 *
 * <p>Because each rule names its own key, locating the value never has to guess between the keys of
 * different block shapes.
 *
 * @param markers  the text every matching block contains, conventionally quoted to match whole names
 * @param fieldKey the JSON key introducing the text, up to and including its opening quote
 */
public record FragmentRule(Set<String> markers, String fieldKey) {

    // copied so the rule cannot be mutated through the set it was built from
    public FragmentRule {
        markers = Set.copyOf(markers);
    }

    boolean matches(String block) {
        return this.markers.stream().allMatch(block::contains);
    }
}
