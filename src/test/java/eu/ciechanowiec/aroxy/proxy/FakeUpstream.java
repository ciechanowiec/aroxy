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
        this(reply, ReplyEnding.COMPLETE);
    }

    FakeUpstream(CannedReply reply, ReplyEnding ending) {
        this.reply = reply;
        this.response = new ResponseGate(ending);
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
     * The encoding header the last request carried, matched without regard to case, so a test
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
                this.deliver(response, payload, callback);
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

        private void deliver(Response response, byte[] payload, Callback callback) throws IOException {
            if (FakeUpstream.this.response.ending() == ReplyEnding.COMPLETE) {
                this.writeComplete(response, payload, callback);
            } else {
                this.writeOpen(response, payload, callback);
            }
        }

        private void writeComplete(Response response, byte[] payload, Callback callback) throws IOException {
            response.getHeaders().put("Content-Length", payload.length);
            write(response, payload);
            FakeUpstream.this.response.payloadWritten();
            callback.succeeded();
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
                this.end(response, callback);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                callback.failed(exception);
            }
        }

        /**
         * Ends a stream whose payload is already out. A truncated reply fails the exchange instead of
         * writing the terminating chunk, so the connection dies with the response committed, which is
         * what a stream broken in flight looks like to the proxy.
         *
         * @param response the sink the terminating chunk would go to
         * @param callback the exchange callback completed or failed here
         */
        private void end(Content.Sink response, Callback callback) {
            if (FakeUpstream.this.response.ending() == ReplyEnding.TRUNCATED) {
                callback.failed(new IOException("upstream truncated the stream"));
            } else {
                response.write(true, ByteBuffer.allocate(0), callback);
            }
        }
    }

    private static final class ResponseGate {

        private final ReplyEnding ending;
        private final CountDownLatch payload;
        private final CountDownLatch eof;

        ResponseGate(ReplyEnding ending) {
            this.ending = ending;
            this.payload = new CountDownLatch(1);
            this.eof = new CountDownLatch(ending == ReplyEnding.HELD_OPEN ? 1 : 0);
        }

        ReplyEnding ending() {
            return this.ending;
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
