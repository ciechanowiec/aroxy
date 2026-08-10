package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.client.CursorTransport;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import eu.ciechanowiec.aroxy.obfuscation.StreamDeobfuscator;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Restores one Cursor protobuf response stream, holding back a token split across two frames.
 *
 * <p>The leaves named by {@link CursorTransport#isDelta(List)} are fragments of one logical text, so
 * they run through a {@link StreamDeobfuscator} whose carry buffer spans frames. Every other leaf
 * arrives whole and is reversed in one pass. A partial still held once the stream ends is emitted by
 * replaying the frame that produced it with the fragment field set to the remainder, which is what the
 * SSE clients do too: authoring a frame instead would mean inventing the bookkeeping fields Cursor's
 * own parser requires.
 *
 * <p>Only the fragment run keeps state. Every whole leaf is reversed by a pure call whose count travels
 * back inside the {@link ProtobufChange}, so a reading the transformer tries and then discards takes its
 * count with it. Counting through a shared field instead would let a discarded reading inflate the
 * audit, and would count the replay frame's untouched leaves a second time.
 */
final class CursorDeltaStream {

    private final CursorTransport transport;
    private final ProtobufTransformer protobuf;
    private final ProtobufText text;
    private final StreamDeobfuscator fragments;
    private Optional<ConnectJsonFrame> carrier;
    private int wholeReversed;

    CursorDeltaStream(CursorTransport transport, Obfuscator obfuscator, ProtobufTransformer protobuf) {
        this.transport = transport;
        this.protobuf = protobuf;
        this.text = new ProtobufText(obfuscator);
        this.fragments = new StreamDeobfuscator(obfuscator);
        this.carrier = Optional.empty();
        this.wholeReversed = 0;
    }

    ConnectJsonFrame accept(ConnectJsonFrame frame) {
        ProtobufInbound restored = this.protobuf.inbound(
            frame.payload(), this.leaf(this.fragments::accept)
        );
        this.wholeReversed += restored.reversed();
        this.carrier = this.fragments.holding() ? Optional.of(frame) : Optional.empty();
        return new ConnectJsonFrame(frame.flags(), restored.payload());
    }

    /**
     * The frame carrying whatever partial token is still held, once the stream has ended.
     *
     * @return the replay frame, empty when nothing was held back
     */
    Optional<ConnectJsonFrame> finish() {
        String held = this.fragments.flush();
        return held.isEmpty() ? Optional.empty() : this.carrier.map(frame -> this.replay(frame, held));
    }

    int reversedCount() {
        return this.wholeReversed + this.fragments.reversedCount();
    }

    // Replaying re-runs the same walk over the same bytes, and every non-delta leaf is restored by the
    // same pure function as before, so the transformer takes an identical path through the message and
    // the fragment field is reached exactly where it was the first time. Its count is dropped rather
    // than added, because those leaves were already counted when the frame first went through.
    private ConnectJsonFrame replay(ConnectJsonFrame frame, String held) {
        byte[] payload = this.protobuf.inbound(frame.payload(), this.leaf(_ -> held)).payload();
        return new ConnectJsonFrame(frame.flags(), payload);
    }

    private ProtobufLeaf leaf(UnaryOperator<String> delta) {
        return new DeltaLeaf(this.transport, delta, this.text);
    }

    /**
     * Routes a leaf by whether Cursor streams it in fragments, and tells the transformer not to guess at
     * the fragment paths. Those are the paths whose transform carries a buffer between calls, so a
     * speculative reading of one would have fed that buffer whether the reading was kept or not.
     */
    private record DeltaLeaf(
        CursorTransport transport, UnaryOperator<String> delta, ProtobufText whole
    ) implements ProtobufLeaf {

        @Override
        public ProtobufChange transform(byte[] content, List<Integer> path) {
            return this.transport.isDelta(path)
                ? ProtobufText.rewritten(content, this.delta)
                : this.whole.inbound(content);
        }

        @Override
        public boolean isKnownText(List<Integer> path) {
            return this.transport.isDelta(path);
        }
    }
}
