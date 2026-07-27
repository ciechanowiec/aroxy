package eu.ciechanowiec.aroxy.client;

import java.util.List;
import java.util.Objects;

/**
 * Cursor's transport: a native bidirectional Connect-protobuf stream through Run over HTTP/2, plus
 * the RunSSE and BidiAppend HTTP/1.1 compatibility methods.
 *
 * <p>Cursor streams its reply as protobuf frames whose text arrives a few characters at a time, so an
 * aroxy token is routinely split across two frames and can only be reversed by holding the trailing
 * partial back until a later frame completes it. Which methods stream, which fields carry that chunked
 * text, and how the JSON dialect of the same stream is shaped are all facts about Cursor rather than
 * about the proxy, so they live here as data and the {@code proxy} package names none of them.
 *
 * @param streamingPaths the request paths served as a bidirectional stream rather than buffered
 * @param deltaPaths     the field-number paths, from the frame root, of the values streamed in fragments
 * @param json           the shape of the same stream in its Connect-JSON dialect
 */
public record CursorTransport(
    List<String> streamingPaths, List<List<Integer>> deltaPaths, CursorJsonShape json
) implements ClientTransport {

    // The components are frozen here and nowhere else: an accessor that copied again would allocate on
    // every read to defend a value already immutable. An incomplete transport is rejected at
    // registration time rather than while a stream is being reversed, as BufferedTransport does.
    public CursorTransport {
        streamingPaths = List.copyOf(streamingPaths);
        deltaPaths = List.copyOf(deltaPaths.stream().map(List::copyOf).toList());
        Objects.requireNonNull(json);
    }

    /**
     * Whether the given request path is served as a bidirectional stream.
     *
     * @param path the request path
     * @return whether that path streams
     */
    public boolean isStreaming(String path) {
        return this.streamingPaths.contains(path);
    }

    /**
     * Whether the value at the given path arrives in fragments rather than whole.
     *
     * @param path the field-number path of a text leaf
     * @return whether that leaf is a streamed fragment
     */
    public boolean isDelta(List<Integer> path) {
        return this.deltaPaths.contains(path);
    }
}
