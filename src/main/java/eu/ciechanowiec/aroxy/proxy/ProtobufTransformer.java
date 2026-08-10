package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.airness.Justification;
import eu.ciechanowiec.aroxy.obfuscation.ObfuscationMatch;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Rewrites text leaves in an otherwise unknown protobuf message while preserving every untouched
 * field byte-for-byte.
 *
 * <p>Cursor nests the binary {@code AgentService.Run} request inside
 * {@code BidiService.BidiAppend.data_binary}. Descriptors for those messages are not part of aroxy, so
 * the transformer follows protobuf's self-describing wire structure. Wire type 2 is ambiguous - it
 * carries a string, a bytes value or a nested message, and nothing on the wire says which - so both
 * readings are attempted and the one that actually matches a configured rule is kept. See
 * {@link #lengthDelimited(byte[], ProtobufLeaf, List)} for why neither reading is safe to try alone.
 * Packed numeric fields and arbitrary bytes remain unchanged unless they contain a configured textual
 * match.
 *
 * <p>Every leaf is handed to a {@link ProtobufLeaf} together with its field-number path from the
 * message root, so a caller can single out one field - Cursor's streamed text delta, say - without the
 * transformer knowing anything about that vendor's schema.
 */
@Justification(
    "composed class: it holds the whole wire-format walk, and coupling is capped by Checkstyle"
)
@SuppressWarnings(
    "ClassWithTooManyDependencies"
)
final class ProtobufTransformer {

    private static final int MAX_DEPTH = 64;
    private final ProtobufText text;

    ProtobufTransformer(ProtobufText text) {
        this.text = text;
    }

    ProtobufOutbound outbound(byte[] message) {
        ProtobufChange transformed = this.requireMessage(
            message, (content, _) -> this.text.outbound(content)
        );
        return new ProtobufOutbound(transformed.bytes(), transformed.matches());
    }

    ProtobufInbound inbound(byte[] message) {
        return this.inbound(message, (content, _) -> this.text.inbound(content));
    }

    /**
     * Restores a message, routing every text leaf through the given transform.
     *
     * @param message the raw protobuf message
     * @param leaf    the transform applied to each text leaf, by path
     * @return the restored message
     */
    ProtobufInbound inbound(byte[] message, ProtobufLeaf leaf) {
        ProtobufChange transformed = this.requireMessage(message, leaf);
        return new ProtobufInbound(transformed.bytes(), transformed.reversed());
    }

    private ProtobufChange requireMessage(byte[] message, ProtobufLeaf leaf) {
        if (message.length == 0) {
            return ProtobufChange.unchanged(message);
        }
        return this.message(message, leaf, List.of())
            .orElseThrow(() -> new ConnectProtocolException("Malformed Cursor protobuf payload"));
    }

    private Optional<ProtobufChange> message(byte[] input, ProtobufLeaf leaf, List<Integer> path) {
        if (path.size() > MAX_DEPTH) {
            return Optional.empty();
        }
        return this.parseMessage(input, leaf, path);
    }

    private Optional<ProtobufChange> parseMessage(byte[] input, ProtobufLeaf leaf, List<Integer> path) {
        try {
            return Optional.of(this.parseValidMessage(input, leaf, path));
        } catch (InvalidProtobufException _) {
            return Optional.empty();
        }
    }

    private ProtobufChange parseValidMessage(byte[] input, ProtobufLeaf leaf, List<Integer> path) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(input.length);
        Collection<ObfuscationMatch> matches = new ArrayList<>();
        int reversed = 0;
        int cursor = 0;
        boolean changed = false;
        while (cursor < input.length) {
            int start = cursor;
            ProtobufWire.Varint key = ProtobufWire.varint(input, start);
            ensureFieldNumber(key);
            int wire = ProtobufWire.wireType(key.value());
            if (wire == 2) {
                ProtobufWire.Varint length = ProtobufWire.varint(input, key.end());
                int contentEnd = ProtobufWire.end(
                    length.end(), length.value(), input.length
                );
                ProtobufChange transformed = this.lengthDelimited(
                    ProtobufWire.slice(input, length.end(), contentEnd),
                    leaf, child(path, key)
                );
                writeLengthField(
                    output,
                    ProtobufWire.slice(input, start, key.end()),
                    ProtobufWire.slice(input, key.end(), contentEnd),
                    transformed
                );
                matches.addAll(transformed.matches());
                reversed += transformed.reversed();
                changed |= transformed.changed();
                cursor = contentEnd;
            } else {
                cursor = ProtobufWire.skip(input, key.end(), wire);
                output.write(input, start, cursor - start);
            }
        }
        return new ProtobufChange(output.toByteArray(), matches, reversed, changed);
    }

    // Wire type 2 carries a string, a bytes value or a nested message, and nothing on the wire tells
    // them apart, so both readings are attempted and the one that actually matches a rule wins. Order
    // alone is not safe in either direction: prose such as "Ask about Helios" parses as a well-formed
    // message, so message-first would recurse past the secret and let it egress, while a nested message
    // whose bytes happen to be printable reads as text, so text-first would rewrite it as one opaque
    // string and leave its inner length prefixes describing the pre-obfuscation size. When both
    // readings match, the message reading wins: it rewrites every length prefix on the path and so
    // stays well-formed even when the guess was wrong.
    //
    // A path the leaf declares as text is exempt from all of that. Guessing there would be guessing at
    // something the caller already knows, and it would run the leaf on a reading that is then thrown
    // away - harmless for a pure transform, but not for one carrying state between calls.
    private ProtobufChange lengthDelimited(byte[] content, ProtobufLeaf leaf, List<Integer> path) {
        if (leaf.isKnownText(path)) {
            return leaf.transform(content, path);
        }
        Optional<ProtobufChange> nested = this.message(content, leaf, path);
        return nested.filter(ProtobufChange::changed)
            .orElseGet(() -> asLeaf(content, leaf, path, nested));
    }

    private static ProtobufChange asLeaf(
        byte[] content, ProtobufLeaf leaf, List<Integer> path, Optional<ProtobufChange> nested
    ) {
        ProtobufChange asText = leaf.transform(content, path);
        return asText.changed() || nested.isEmpty() ? asText : nested.orElseThrow();
    }

    // Qodana sees only that this body needs Collection, but the ordering List promises is the whole
    // point: a path is a sequence of field numbers, and an unordered parameter would accept a set and
    // silently build a path whose elements are in the wrong order.
    @Justification(
        "the field-number path is ordered by definition, which Collection would not promise"
    )
    @SuppressWarnings(
        "TypeMayBeWeakened"
    )
    private static List<Integer> child(List<Integer> path, ProtobufWire.Varint key) {
        return Stream.concat(
            path.stream(), Stream.of(Math.toIntExact(ProtobufWire.fieldNumber(key.value())))
        ).toList();
    }

    private static void writeLengthField(
        ByteArrayOutputStream output, byte[] prefix,
        byte[] originalValue, ProtobufChange transformed
    ) {
        output.writeBytes(prefix);
        if (transformed.changed()) {
            // Read once: the accessor clones, and this runs per field of per frame on a stream Cursor
            // chunks at roughly eleven characters.
            byte[] rewritten = transformed.bytes();
            ProtobufWire.writeVarint(output, rewritten.length);
            output.writeBytes(rewritten);
        } else {
            output.writeBytes(originalValue);
        }
    }

    private static void ensureFieldNumber(ProtobufWire.Varint key) {
        if (ProtobufWire.fieldNumber(key.value()) == 0) {
            throw new InvalidProtobufException();
        }
    }
}
