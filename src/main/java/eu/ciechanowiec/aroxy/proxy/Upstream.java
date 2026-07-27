package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.client.Gateway;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Forwards requests to one client's upstream API over a plain {@link HttpClient} (HTTP/1.1, a fixed
 * connect timeout, and the JDK's default proxy selector and trust store). It forces
 * {@code Accept-Encoding: identity} so the response body stays transformable, drops the hop-by-hop
 * and restricted request headers, and forwards everything else - including the authentication
 * headers - unchanged. The upstream base address comes from {@link Gateway#upstream()}, so each
 * client's listener has one unambiguous destination and no request is routed by guessing.
 *
 * <p>Redirects are deliberately not followed ({@link HttpClient.Redirect#NEVER}): a 3xx is passed
 * straight back to the client untouched. A pass-through proxy has no business chasing a redirect, and
 * not doing so guarantees the forwarded authentication headers can never be re-sent to a redirect
 * target on a different host.
 */
public final class Upstream {

    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private final HttpClient client;
    private final String base;

    /**
     * Creates an upstream client targeting one client's API base.
     *
     * @param gateway the gateway carrying the upstream base address
     */
    public Upstream(Gateway gateway) {
        this.base = gateway.upstream().toString();
        this.client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build();
    }

    /**
     * Sends one request and returns the streamed reply (status, headers, body stream).
     */
    public UpstreamReply send(
        String method, String pathAndQuery, Map<String, List<String>> headers, byte[] body
    ) throws IOException, InterruptedException {
        HttpRequest request = this.buildRequest(method, pathAndQuery, headers, body);
        HttpResponse<InputStream> response = this.client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        return new UpstreamReply(response.statusCode(), response.headers().map(), response.body());
    }

    private HttpRequest buildRequest(
        String method, String pathAndQuery,
        Map<String, List<String>> headers, byte[] body
    ) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(this.base + pathAndQuery))
            .method(method, publisher(body));
        headers.forEach((name, values) -> forward(builder, name, values));
        builder.header("Accept-Encoding", "identity");
        return builder.build();
    }

    private static HttpRequest.BodyPublisher publisher(byte[] body) {
        boolean empty = body.length == 0;
        return empty ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body);
    }

    private static void forward(HttpRequest.Builder builder, String name, Iterable<String> values) {
        if (RestrictedHeaders.isForbidden(name)) {
            return;
        }
        values.forEach(value -> tryHeader(builder, name, value));
    }

    private static void tryHeader(HttpRequest.Builder builder, String name, String value) {
        try {
            builder.header(name, value);
        } catch (IllegalArgumentException _) {
            // HttpClient restricts a few header names, so skip rather than fail the whole request.
        }
    }

    String base() {
        return this.base;
    }
}
