package eu.ciechanowiec.aroxy.proxy;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

/**
 * What a {@link VirtualThreadExchange} hands one exchange to. A router is free to block, because the
 * exchange that calls it owns a virtual thread of its own.
 *
 * <p>The routers are bound to this by method reference rather than by declaring that they implement
 * it. That keeps their own entry points package private, which is what they are otherwise, and leaves
 * each of them free to name its parameters after the protocol it serves.
 */
@FunctionalInterface
interface ExchangeRouter {

    void serve(Request request, Response response);
}
