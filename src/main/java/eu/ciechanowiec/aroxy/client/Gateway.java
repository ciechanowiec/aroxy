package eu.ciechanowiec.aroxy.client;

import java.net.URI;

/**
 * One client's listener: the local port it binds and the upstream that listener forwards to. Pairing
 * the two is what makes a socket unambiguous about its destination, so no request has to be routed by
 * guessing which vendor a path belongs to.
 *
 * <p>The upstream is fixed per client and is not configurable. Only the port is. {@link #withUpstream}
 * exists so tests can point a listener at an in-JVM stand-in.
 *
 * @param listenPort the local port this client's listener binds
 * @param upstream   the API base address this listener forwards to
 */
public record Gateway(int listenPort, URI upstream) {

    /**
     * Returns a copy bound to the given port, keeping the upstream.
     *
     * @param port the port to bind instead
     * @return a copy of this gateway bound to the given port
     */
    public Gateway withPort(int port) {
        return new Gateway(port, this.upstream);
    }

    /**
     * Returns a copy forwarding to the given upstream, keeping the port.
     *
     * @param newUpstream the API base address to forward to instead
     * @return a copy of this gateway pointed at the given upstream
     */
    public Gateway withUpstream(URI newUpstream) {
        return new Gateway(this.listenPort, newUpstream);
    }
}
