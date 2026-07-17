package eu.ciechanowiec.aroxy.proxy;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The parsed inbound request: its method, the raw path, the path-and-query to forward, the request
 * headers, and the fully-read body. The path drives two decisions - whether the body is obfuscated
 * before egress and whether the response is deobfuscated. Both use the same path-segment match
 * ({@code /v1/messages} itself or any {@code /v1/messages/...} sub-resource such as
 * {@code count_tokens}), so a request that is obfuscated always has its response deobfuscated and no
 * token can survive into the client. A near-miss such as {@code /v1/messagesX} is not a sub-resource
 * and stays a transparent passthrough, as is any other path. A request whose body carries a
 * non-identity {@code Content-Encoding} is passed through unobfuscated rather than decoded as text,
 * which would mangle the compressed bytes.
 *
 * @param method       the HTTP method
 * @param pathAndQuery the path plus query string to send upstream
 * @param path         the raw request path, used for routing decisions
 * @param headers      the inbound request headers
 * @param body         the fully-read request body
 */
// mutable-component exposure is accepted (see spotbugs-exclude.xml EI_EXPOSE); byte[] body cloned in and out
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

    private static final String MESSAGES = "/v1/messages";

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

    boolean shouldObfuscate() {
        return this.isMessages() && this.body.length > 0 && !this.isEncoded();
    }

    boolean shouldDeobfuscate() {
        return this.isMessages();
    }

    private boolean isMessages() {
        return MESSAGES.equals(this.path) || this.path.startsWith(MESSAGES + "/");
    }

    private boolean isEncoded() {
        return HeaderLookup.first(this.headers, "content-encoding")
            .filter(value -> !value.isBlank())
            .filter(value -> !"identity".equalsIgnoreCase(value))
            .isPresent();
    }

    @Override
    public byte[] body() {
        return this.body.clone();
    }
}
