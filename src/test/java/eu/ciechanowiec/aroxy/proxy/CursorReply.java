package eu.ciechanowiec.aroxy.proxy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Reads a transformed Cursor reply back apart, as the counterpart to the fixtures {@link CursorWire}
 * writes. Some of what the reply must get right is invisible to a substring scan of the whole stream:
 * which envelope a value landed in, and which field of it. Both need the reply taken apart the way the
 * agent takes it apart.
 */
@UtilityClass
final class CursorReply {

    /**
     * Splits a reply into its envelopes, so a test can assert on their order and count.
     *
     * @param stream the bytes of a Connect reply
     * @return the envelopes it carries, in order
     */
    @SneakyThrows
    static List<ConnectJsonFrame> frames(byte[] stream) {
        Collection<ConnectJsonFrame> frames = new ArrayList<>();
        InputStream input = new ByteArrayInputStream(stream);
        Optional<ConnectJsonFrame> frame = ConnectJsonFrame.read(input);
        while (frame.isPresent()) {
            frames.add(frame.orElseThrow());
            frame = ConnectJsonFrame.read(input);
        }
        return List.copyOf(frames);
    }

    /**
     * The text an envelope carries at one field-number path, read through the production walk with that
     * path declared as text so the walk stops there rather than guessing at it. A value written anywhere
     * else leaves this empty, which is what lets a test tell one nesting level from another.
     *
     * @param frame the envelope to read
     * @param path  the field numbers from the message root down to the value
     * @return the text at that path, empty when nothing sits there
     */
    static String valueAt(ConnectJsonFrame frame, List<Integer> path) {
        PathProbe probe = new PathProbe(path);
        new ProtobufTransformer(new ProtobufText(ProxyHarness.obfuscator()))
            .inbound(frame.payload(), probe);
        return probe.seen();
    }

    private static final class PathProbe implements ProtobufLeaf {

        private final List<Integer> wanted;
        private final Collection<String> seen;

        PathProbe(Collection<Integer> wanted) {
            this.wanted = List.copyOf(wanted);
            this.seen = new ArrayList<>();
        }

        @Override
        public ProtobufChange transform(byte[] content, List<Integer> path) {
            if (this.wanted.equals(path)) {
                this.seen.add(new String(content, StandardCharsets.UTF_8));
            }
            return ProtobufChange.unchanged(content);
        }

        @Override
        public boolean isKnownText(List<Integer> path) {
            return this.wanted.equals(path);
        }

        String seen() {
            return String.join("", this.seen);
        }
    }
}
