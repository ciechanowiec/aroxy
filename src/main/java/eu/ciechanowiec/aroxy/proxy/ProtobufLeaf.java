package eu.ciechanowiec.aroxy.proxy;

import java.util.List;

/**
 * Transforms one length-delimited value that has been read as a text leaf rather than as a nested
 * message. The path is the chain of protobuf field numbers from the message root down to the value,
 * which is what lets a caller treat one specific field differently from every other leaf.
 *
 * <p>A transform must tolerate being called on a reading that {@link ProtobufTransformer} then
 * discards, because wire type 2 is ambiguous and both readings of a length-delimited value are
 * attempted. A transform that cannot - one holding a carry buffer across calls, say - names the paths
 * it owns through {@link #isKnownText(List)}, and the transformer stops guessing at those.
 */
@FunctionalInterface
interface ProtobufLeaf {

    /**
     * Transforms the leaf at the given path.
     *
     * @param content the raw bytes of the length-delimited value
     * @param path    the field numbers from the message root down to this value
     * @return the transformation outcome
     */
    ProtobufChange transform(byte[] content, List<Integer> path);

    /**
     * Whether the value at this path is known to be text, so the transformer must not try to read it as
     * a nested message first.
     *
     * <p>Declaring a path spares it a guess the caller already knows the answer to, and it is the only
     * way a stateful transform can be safe: a speculative reading that is discarded still ran, and an
     * effect the transformer discards is one the transform has already applied. The default is
     * {@code false}, which suits any transform that is a pure function of its input
     *
     * @param path the field numbers from the message root down to the value
     * @return whether that path is declared to carry text
     */
    default boolean isKnownText(List<Integer> path) {
        return false;
    }
}
