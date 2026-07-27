package eu.ciechanowiec.aroxy.client;

import java.util.Objects;

/**
 * A conventional request/response transport whose request is read in full before it is forwarded.
 *
 * @param requestBody how the complete request body is scanned
 * @param dialect     the dialect used when the response is SSE
 */
public record BufferedTransport(RequestBodyPolicy requestBody, SseDialect dialect)
                               implements ClientTransport {

    public BufferedTransport {
        Objects.requireNonNull(requestBody);
        Objects.requireNonNull(dialect);
    }
}
