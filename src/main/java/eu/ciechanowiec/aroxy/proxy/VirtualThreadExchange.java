package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.airness.Justification;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

/**
 * Adapts an {@link ExchangeRouter} to Jetty's handler and lifecycle contracts. Every listener aroxy
 * runs on Jetty shares this one, because what differs between them is the router and not the way an
 * exchange is carried.
 *
 * <p>Each exchange is served on its own virtual thread because a router blocks: it streams the request
 * upstream while the response is still arriving. A failure is reported through the thread's uncaught
 * handler rather than a catch block, so an unexpected fault still fails the Jetty callback instead of
 * vanishing.
 *
 * <p>The threads are tracked so shutting a listener down waits for what is still in flight. That is
 * not merely tidy: an exchange writes its audit record and its wire dump after the last byte has
 * reached the agent, and a teardown that did not wait would cut those writes short. Waiting is
 * therefore {@link #drain()}, a step of its own that a listener runs before it stops its server,
 * because stopping the server first is the very thing the wait exists to avoid. Releasing whatever the
 * router holds comes last, and belongs to the listener that built the router.
 *
 * <p>The wait is one budget for the whole drain rather than one per thread. A budget per thread reads
 * the same on a quiet shutdown and multiplies by the number of live streams on a busy one, which is
 * when a shutdown can least afford to hang.
 */
final class VirtualThreadExchange extends Handler.Abstract {

    private static final Duration DRAIN = Duration.ofSeconds(5);
    private final ExchangeRouter router;
    private final Set<Thread> inFlight;

    VirtualThreadExchange(ExchangeRouter router) {
        this.router = router;
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

    /**
     * Waits, up to one shared budget, for the exchanges still in flight to finish writing.
     */
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

    // A deadline already past yields a non-positive duration, which Thread::join returns from at once,
    // so the budget runs out for the drain as a whole rather than being handed out again per thread.
    @Justification(
        "a deadline is an instant, and Temporal would accept a date or a time of day as one"
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
