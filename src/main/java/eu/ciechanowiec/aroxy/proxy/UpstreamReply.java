package eu.ciechanowiec.aroxy.proxy;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * A streamed upstream response: the status code, a read-only view of the response headers, and the
 * body as an {@link InputStream} so it can be transformed or copied without being buffered whole.
 *
 * @param status  the HTTP status code returned by the upstream
 * @param headers the upstream response headers, keyed exactly as received
 * @param body    the response body stream, owned by the caller and closed by it
 */
public record UpstreamReply(int status, Map<String, List<String>> headers, InputStream body) {

    public UpstreamReply {
        headers = Map.copyOf(headers);
    }
}
