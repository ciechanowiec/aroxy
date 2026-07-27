package eu.ciechanowiec.aroxy.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.ProxyConfiguration;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.transport.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.util.component.LifeCycle;

/**
 * Cursor's true bidirectional HTTP/2 upstream.
 *
 * <p>The JDK client does not expose response headers until an open request publisher finishes.
 * Cursor's native {@code Run} request deliberately remains open while the response arrives, so this
 * client uses Jetty's asynchronous request content and streaming response listener instead. That is
 * the whole reason aroxy carries two HTTP clients: {@link Upstream} keeps the buffered clients on the
 * JDK's, which is the simpler tool for a request that is complete before it is sent.
 *
 * <p>Carrying two clients means their reach has to be made to agree deliberately, because their
 * defaults do not. The trust store already agrees: Jetty's client, given no key store of its own,
 * builds its SSL context from the platform default trust managers, which is the store
 * {@link javax.net.ssl.SSLContext#getDefault()} hands the JDK client. The proxy does not, so
 * {@link JdkProxySelection} reads the same {@link ProxySelector} the JDK client consults and hands
 * Jetty the answer.
 */
final class CursorDuplexUpstream implements AutoCloseable {

    private static final long RESPONSE_TIMEOUT_SECONDS = 30;
    // An agent turn can sit quiet while the model thinks, so the idle timeout is disabled outright
    // rather than left to whatever the client's default happens to be. The response timeout above still
    // bounds how long aroxy waits for the response headers, which arrive promptly.
    private static final long IDLE_TIMEOUT_DISABLED = 0;
    private static final String CONNECT_ACCEPT_ENCODING = "connect-accept-encoding";
    private final String base;
    private final HttpClient client;

    CursorDuplexUpstream(String base) {
        this.base = base;
        this.client = new HttpClient(new HttpClientTransportOverHTTP2(new HTTP2Client()));
        this.client.setFollowRedirects(false);
        this.client.setIdleTimeout(IDLE_TIMEOUT_DISABLED);
        JdkProxySelection.proxyFor(ProxySelector.getDefault(), URI.create(base))
            .ifPresent(this.client.getProxyConfiguration()::addProxy);
        LifeCycle.start(this.client);
    }

    UpstreamReply send(
        String method, String pathAndQuery, Map<String, List<String>> headers, InputStream body
    ) throws IOException, InterruptedException {
        CursorDuplexCall call = new CursorDuplexCall(body);
        Request request = this.client.newRequest(URI.create(this.base + pathAndQuery))
            .method(method)
            .headers(target -> copy(headers, target))
            .body(call.content());
        request.send(call.listener());
        call.startUpload();
        try {
            Response response = call.await(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return new UpstreamReply(
                response.getStatus(), HttpFields.asMap(response.getHeaders()), call.responseBody()
            );
        } catch (ExecutionException | TimeoutException exception) {
            call.abort(exception);
            throw new IOException("Cursor HTTP/2 upstream connection failed", exception);
        } catch (InterruptedException exception) {
            call.abort(exception);
            throw exception;
        }
    }

    // What the selection resolves to is pinned by its own unit test. This is how a test sees the other
    // half: that the answer was wired into the client rather than computed and dropped.
    List<ProxyConfiguration.Proxy> proxies() {
        return List.copyOf(this.client.getProxyConfiguration().getProxies());
    }

    private static void copy(Map<String, List<String>> source, HttpFields.Mutable target) {
        source.forEach(
            (name, values) -> {
                if (
                    !RestrictedHeaders.isForbidden(name)
                        && !CONNECT_ACCEPT_ENCODING.equalsIgnoreCase(name)
                ) {
                    values.forEach(value -> target.add(name, value));
                }
            }
        );
        target.put("Accept-Encoding", "identity");
    }

    @Override
    public void close() {
        LifeCycle.stop(this.client);
    }
}
