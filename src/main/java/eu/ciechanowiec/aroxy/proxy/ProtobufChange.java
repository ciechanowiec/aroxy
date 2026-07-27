package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.obfuscation.ObfuscationMatch;
import eu.ciechanowiec.governance.Justification;
import java.util.Collection;
import java.util.List;

/**
 * The result of transforming one protobuf message or text value: the resulting bytes, what the
 * transform found, and whether it rewrote anything.
 *
 * <p>There is no validity flag here. A value that does not parse is not a kind of result, it is the
 * absence of one, so the parser returns an empty {@link java.util.Optional} instead and the two states
 * cannot be confused.
 *
 * @param bytes    the transformed bytes
 * @param matches  what an outbound transform matched
 * @param reversed how many tokens an inbound transform restored
 * @param changed  whether the bytes differ from the input
 */
@Justification(
    "the bytes are cloned by the compact constructor and again by the accessor, so the array never escapes"
)
@SuppressWarnings(
    {"ArrayRecordComponent", "AssignmentOrReturnOfFieldWithMutableType"}
)
record ProtobufChange(byte[] bytes, Collection<ObfuscationMatch> matches, int reversed, boolean changed) {

    ProtobufChange {
        bytes = bytes.clone();
        matches = List.copyOf(matches);
    }

    static ProtobufChange unchanged(byte[] input) {
        return new ProtobufChange(input, List.of(), 0, false);
    }

    @Override
    public byte[] bytes() {
        return this.bytes.clone();
    }

    @Override
    public Collection<ObfuscationMatch> matches() {
        return List.copyOf(this.matches);
    }
}
