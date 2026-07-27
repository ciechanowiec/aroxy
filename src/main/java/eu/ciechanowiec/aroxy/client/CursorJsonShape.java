package eu.ciechanowiec.aroxy.client;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The shape of Cursor's Connect-JSON event stream, expressed as data so no vendor event name has to
 * be written into the proxy. It is the JSON counterpart of the {@link SseDialect} the buffered clients
 * carry, and of {@link CursorTransport#deltaPaths()} on the protobuf side.
 *
 * @param fragmentFields the key under which each fragment-bearing event carries its text, by event key
 * @param flushKeys      the event keys that end a fragment run, so a held partial must be flushed
 * @param turnEndKey     the event key that ends the logical turn, which is what the audit records
 */
public record CursorJsonShape(
    Map<String, String> fragmentFields, Set<String> flushKeys, String turnEndKey
) {

    // Copied here so the shape cannot be mutated through the collections it was built from, and only
    // here: what the compact constructor froze stays frozen without an accessor copying it again.
    public CursorJsonShape {
        fragmentFields = Map.copyOf(fragmentFields);
        flushKeys = Set.copyOf(flushKeys);
        Objects.requireNonNull(turnEndKey);
    }
}
