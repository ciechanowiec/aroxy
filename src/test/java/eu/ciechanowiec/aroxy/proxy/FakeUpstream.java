package eu.ciechanowiec.aroxy.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.component.LifeCycle;

/**
 * A real in-JVM HTTP/1.1 and HTTP/2 upstream that records the last request and returns a canned
 * reply. It stands in for a client's API so the proxy can be driven end to end over real sockets.
 */
final class FakeUpstream {

    private final Server server;
    private final ServerConnector connector;
    private final AtomicReference<Received> last;
    private final AtomicInteger requestCount;
    private final CannedReply reply;
    private final ResponseGate response;

    FakeUpstream(CannedReply reply) {
        this(reply, false);
    }

    FakeUpstream(CannedReply reply, boolean delayedEof) {
        this.reply = reply;
        this.response = new ResponseGate(delayedEof);
        this.last = new AtomicReference<>(new Received("", "", Map.of()));
        this.requestCount = new AtomicInteger();
        this.server = new Server();
        HttpConfiguration configuration = new HttpConfiguration();
        this.connector = new ServerConnector(
            this.server,
            new HttpConnectionFactory(configuration),
            new HTTP2CServerConnectionFactory(configuration)
        );
        this.connector.setHost(LocalHost.HOST);
        this.connector.setPort(0);
        this.server.addConnector(this.connector);
        this.server.setHandler(new CannedEndpoint());
        LifeCycle.start(this.server);
    }

    URI uri() {
        return URI.create("http://" + LocalHost.HOST + ":" + this.connector.getLocalPort());
    }

    String lastBody() {
        return Objects.requireNonNull(this.last.get()).body();
    }

    /**
     * The path and query of the last request, so a test can prove the proxy forwarded the method it was
     * asked for rather than dropping it and hitting the upstream root.
     *
     * @return the last request target
     */
    String lastTarget() {
        return Objects.requireNonNull(this.last.get()).target();
    }

    int requestCount() {
        return this.requestCount.get();
    }

    boolean awaitResponsePayload(Duration timeout) throws InterruptedException {
        return this.response.awaitPayload(timeout);
    }

    void releaseResponseEof() {
        this.response.releaseEof();
    }

    Optional<String> lastAuthorization() {
        return this.lastHeader("Authorization");
    }

    Optional<String> lastHeader(String name) {
        return Objects.requireNonNull(this.last.get()).headers().entrySet().stream()
            .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
            .flatMap(entry -> entry.getValue().stream())
            .findFirst();
    }

    /**
     * The {@code Content-Encoding} the last request carried, matched without regard to case, so a test
     * can assert that a body the proxy inflated no longer claims to be compressed.
     *
     * @return the encoding, or empty when the request declared none
     */
    Optional<String> lastContentEncoding() {
        return this.lastHeader("content-encoding");
    }

    void stop() {
        this.releaseResponseEof();
        LifeCycle.stop(this.server);
    }

    private final class CannedEndpoint extends Handler.Abstract {

        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            try {
                FakeUpstream.this.requestCount.incrementAndGet();
                this.capture(request);
                FakeUpstream.this.reply.headers()
                    .forEach((name, value) -> response.getHeaders().add(name, value));
                byte[] payload = FakeUpstream.this.reply.body().getBytes(StandardCharsets.UTF_8);
                response.setStatus(FakeUpstream.this.reply.status());
                if (FakeUpstream.this.response.delayed()) {
                    this.writeOpen(response, payload, callback);
                } else {
                    response.getHeaders().put("Content-Length", payload.length);
                    write(response, payload);
                    FakeUpstream.this.response.payloadWritten();
                    callback.succeeded();
                }
            } catch (IOException exception) {
                callback.failed(exception);
            }
            return true;
        }

        private void capture(Request request) throws IOException {
            try (InputStream input = Request.asInputStream(request)) {
                FakeUpstream.this.last.set(
                    new Received(
                        new String(input.readAllBytes(), StandardCharsets.UTF_8),
                        request.getHttpURI().getPathQuery(),
                        Map.copyOf(HttpFields.asMap(request.getHeaders()))
                    )
                );
            }
        }

        private void write(Content.Sink response, byte[] payload) throws IOException {
            try (OutputStream output = Content.Sink.asOutputStream(response)) {
                output.write(payload);
            }
        }

        private void writeOpen(Content.Sink response, byte[] payload, Callback callback) {
            response.write(
                false, ByteBuffer.wrap(payload),
                Callback.from(
                    () -> {
                        FakeUpstream.this.response.payloadWritten();
                        Thread.startVirtualThread(() -> this.finish(response, callback));
                    },
                    callback::failed
                )
            );
        }

        private void finish(Content.Sink response, Callback callback) {
            try {
                FakeUpstream.this.response.awaitEof();
                response.write(true, ByteBuffer.allocate(0), callback);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                callback.failed(exception);
            }
        }
    }

    private static final class ResponseGate {

        private final boolean delayed;
        private final CountDownLatch payload;
        private final CountDownLatch eof;

        ResponseGate(boolean delayed) {
            this.delayed = delayed;
            this.payload = new CountDownLatch(1);
            this.eof = new CountDownLatch(delayed ? 1 : 0);
        }

        boolean delayed() {
            return this.delayed;
        }

        void payloadWritten() {
            this.payload.countDown();
        }

        boolean awaitPayload(Duration timeout) throws InterruptedException {
            return this.payload.await(timeout.toNanos(), TimeUnit.NANOSECONDS);
        }

        void awaitEof() throws InterruptedException {
            this.eof.await();
        }

        void releaseEof() {
            this.eof.countDown();
        }
    }

    /**
     * What the upstream last received. The three values are captured together, under one lock-free
     * reference, so a test never reads the target of one request beside the body of another.
     *
     * @param body    the request body, decoded
     * @param target  the request path and query
     * @param headers the request headers
     */
    private record Received(String body, String target, Map<String, List<String>> headers) {
    }
}
