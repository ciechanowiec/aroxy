package eu.ciechanowiec.aroxy.proxy;

import com.sun.net.httpserver.HttpExchange;
import eu.ciechanowiec.airness.Justification;
import eu.ciechanowiec.aroxy.client.PathScope;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The parsed inbound request: its method, the raw path, the path-and-query to forward, the request
 * headers, and the fully-read body. Which paths are transformed is not decided here but by the
 * serving client's {@link PathScope}, so this record carries no vendor's endpoint names.
 *
 * <p>A body carrying a {@code Content-Encoding} this proxy can undo is inflated first, by
 * {@link #decoded()}. One it cannot undo remains encoded so the serving exchange can reject it when
 * the path is in scope, rather than decoding it as text or silently forwarding sensitive bytes.
 *
 * @param method       the HTTP method
 * @param pathAndQuery the path plus query string to send upstream
 * @param path         the raw request path, used for routing decisions
 * @param headers      the inbound request headers
 * @param body         the fully-read request body
 */
@Justification(
    "mutable-component exposure is accepted (see spotbugs-exclude.xml EI_EXPOSE), byte[] body cloned in and out"
)
@SuppressWarnings(
    {"ArrayRecordComponent", "AssignmentOrReturnOfFieldWithMutableType"}
)
record ProxyRequest(
    String method,
    String pathAndQuery,
    String path,
    Map<String, List<String>> headers,
    byte[] body
) {

    ProxyRequest {
        body = body.clone();
    }

    static ProxyRequest from(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();
        String path = uri.getRawPath();
        String query = Optional.ofNullable(uri.getRawQuery()).map(raw -> "?" + raw).orElse("");
        byte[] body = exchange.getRequestBody().readAllBytes();
        return new ProxyRequest(
            exchange.getRequestMethod(), path + query, path,
            exchange.getRequestHeaders(), body
        );
    }

    /**
     * This request with any encoding this proxy can undo already undone, or this request unchanged
     * when there is nothing to undo or the body does not inflate. An unchanged encoded request is
     * deliberately distinguishable through {@link #isEncoded()} so an in-scope exchange fails closed.
     *
     * <p>The inflated copy drops its {@code Content-Encoding} header, and that is load-bearing:
     * {@link RestrictedHeaders} deliberately forwards that header, so leaving it would announce
     * {@code zstd} over bytes that are no longer compressed and the upstream would reject the request.
     * It is dropped here rather than restricted globally, because on the path where the body was
     * <em>not</em> inflated the header is still true and must survive.
     *
     * @return the decoded request, or this one when nothing was decoded
     */
    ProxyRequest decoded() {
        return this.encoding()
            .filter(ZstdBody.ENCODING::equalsIgnoreCase)
            .flatMap(_ -> ZstdBody.inflated(this.body))
            .map(this::withPlainBody)
            .orElse(this);
    }

    private ProxyRequest withPlainBody(byte[] plain) {
        return new ProxyRequest(this.method, this.pathAndQuery, this.path, withoutEncoding(this.headers), plain);
    }

    private static Map<String, List<String>> withoutEncoding(Map<String, List<String>> headers) {
        return headers.entrySet().stream()
            .filter(entry -> !"content-encoding".equalsIgnoreCase(entry.getKey()))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    boolean hasBody() {
        return this.body.length > 0;
    }

    boolean isEncoded() {
        return this.encoding().isPresent();
    }

    Optional<String> encoding() {
        return HeaderLookup.first(this.headers, "content-encoding")
            .filter(value -> !value.isBlank())
            .filter(value -> !"identity".equalsIgnoreCase(value));
    }

    @Override
    public byte[] body() {
        return this.body.clone();
    }
}
