package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.client.ClientProfile;
import eu.ciechanowiec.aroxy.client.CopilotTransport;
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
import java.util.Optional;

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
    private final boolean dynamic;

    /**
     * Creates an upstream client targeting one client's API base.
     *
     * @param gateway the gateway carrying the upstream base address
     */
    public Upstream(Gateway gateway) {
        this(gateway, false);
    }

    private Upstream(Gateway gateway, boolean dynamic) {
        this.base = gateway.upstream().toString();
        this.dynamic = dynamic;
        this.client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build();
    }

    /**
     * Creates the upstream mode selected by a profile and its effective gateway. A forward proxy uses
     * the request destination in production. A test seam whose gateway differs from the registered
     * one remains fixed on its in-JVM upstream.
     *
     * @param gateway the effective gateway
     * @param profile the registered client profile
     * @return an upstream with the profile's destination policy
     */
    public static Upstream forProfile(Gateway gateway, ClientProfile profile) {
        boolean forwardProxy = profile.transport() instanceof CopilotTransport;
        boolean unchanged = gateway.upstream().equals(profile.gateway().upstream());
        return new Upstream(gateway, forwardProxy && unchanged);
    }

    /**
     * Sends one request and returns the streamed reply (status, headers, body stream).
     *
     * @param method       the HTTP method to forward
     * @param pathAndQuery the path and query to append to the upstream base
     * @param headers      the request headers to forward
     * @param body         the request body to forward
     * @return the streamed upstream reply
     * @throws IOException          if the request cannot be sent or its response cannot be received
     * @throws InterruptedException if the request is interrupted
     */
    public UpstreamReply send(
        String method, String pathAndQuery, Map<String, List<String>> headers, byte[] body
    ) throws IOException, InterruptedException {
        HttpRequest request = this.buildRequest(method, pathAndQuery, headers, body);
        HttpResponse<InputStream> response = this.client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        return new UpstreamReply(response.statusCode(), response.headers().map(), response.body());
    }

    /**
     * Sends one request to an explicit HTTPS destination. Forward-proxy transports use this after
     * validating the destination host against their own allowlist.
     *
     * @param destination the complete upstream request {@link URI}
     * @param method      the HTTP method to forward
     * @param headers     the request headers to forward
     * @param body        the request body to forward
     * @return the streamed upstream reply
     * @throws IOException          if the request cannot be sent or its response cannot be received
     * @throws InterruptedException if the request is interrupted
     */
    public UpstreamReply sendTo(
        URI destination, String method, Map<String, List<String>> headers, byte[] body
    ) throws IOException, InterruptedException {
        URI effective = this.dynamic ? destination : URI.create(this.base + pathAndQuery(destination));
        HttpRequest request = this.buildRequest(effective, method, headers, body);
        HttpResponse<InputStream> response = this.client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        return new UpstreamReply(response.statusCode(), response.headers().map(), response.body());
    }

    private static String pathAndQuery(URI destination) {
        return Optional.ofNullable(destination.getRawQuery())
            .map(query -> destination.getRawPath() + "?" + query)
            .orElse(destination.getRawPath());
    }

    private HttpRequest buildRequest(
        String method, String pathAndQuery,
        Map<String, List<String>> headers, byte[] body
    ) {
        return this.buildRequest(URI.create(this.base + pathAndQuery), method, headers, body);
    }

    private HttpRequest buildRequest(
        URI destination, String method, Map<String, List<String>> headers, byte[] body
    ) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(destination)
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
