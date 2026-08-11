package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.airness.Justification;
import eu.ciechanowiec.aroxy.client.CopilotTransport;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

/**
 * Runs each decrypted Copilot exchange on a virtual thread so the blocking upstream client can stream
 * a response without occupying Jetty's selector threads.
 */
final class CopilotExchange extends Handler.Abstract {

    private static final Duration DRAIN = Duration.ofSeconds(5);
    private final CopilotRouter router;
    private final Set<Thread> inFlight;

    CopilotExchange(ProxyParts parts, CopilotTransport transport) {
        this.router = new CopilotRouter(parts, transport);
        this.inFlight = ConcurrentHashMap.newKeySet();
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) {
        Thread worker = Thread.ofVirtual()
            .uncaughtExceptionHandler((_, failure) -> callback.failed(failure))
            .unstarted(() -> this.serve(request, response, callback));
        this.inFlight.add(worker);
        worker.start();
        return true;
    }

    void drain() {
        Instant deadline = Instant.now().plus(DRAIN);
        this.inFlight.forEach(worker -> join(worker, deadline));
    }

    private void serve(Request request, Response response, Callback callback) {
        try {
            this.router.serve(request, response);
            callback.succeeded();
        } finally {
            this.inFlight.remove(Thread.currentThread());
        }
    }

    @Justification(
        "a deadline is an instant, while Temporal would also accept a date or a time of day"
    )
    @SuppressWarnings(
        "TypeMayBeWeakened"
    )
    static void join(Thread worker, Instant deadline) {
        try {
            worker.join(Duration.between(Instant.now(), deadline));
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }
}
