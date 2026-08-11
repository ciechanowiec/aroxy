package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.airness.Justification;
import eu.ciechanowiec.aroxy.client.BufferedTransport;
import eu.ciechanowiec.aroxy.client.CopilotTransport;
import eu.ciechanowiec.aroxy.client.CursorTransport;
import eu.ciechanowiec.aroxy.client.Gateway;
import java.io.IOException;

/**
 * One client's local listener, bound to the local host {@code 127.0.0.1} and to that client's
 * {@link Gateway} port (port 0 binds an ephemeral port, exposed via {@link #port()} for tests).
 * Buffered clients use the JDK HTTP/1.1 server. Cursor uses a dual HTTP/1.1 and cleartext HTTP/2
 * listener because its native bidirectional agent stream uses HTTP/2.
 *
 * <p>The transport is a sealed type, so the choice below is exhaustive and each listener receives its
 * transport already narrowed. Nothing downstream has to ask a profile for a shape it may not have.
 *
 * <p>The daemon binds one of these per registered client, which is what lets a single daemon serve
 * several agents while each socket keeps one unambiguous upstream.
 */
public final class ProxyServer {

    private static final int STOP_DELAY_SECONDS = 1;
    private final ProxyListener listener;

    /**
     * Binds this client's listener to the given host and its gateway port, routing every path to the
     * single exchange.
     *
     * @param listenHost the local host to bind
     * @param gateway    the gateway carrying the port to bind
     * @param parts      the obfuscator, upstream, audit log, and client profile the exchange is wired from
     * @throws IOException if the server socket cannot be bound
     */
    @Justification(
        "ClientProfile rejects a null transport before a ProxyServer can be constructed"
    )
    @SuppressWarnings(
        "MissingNullCaseInSwitch"
    )
    public ProxyServer(String listenHost, Gateway gateway, ProxyParts parts) throws IOException {
        this.listener = switch (parts.profile().transport()) {
            case CopilotTransport copilot ->
                new CopilotProxyListener(listenHost, gateway.listenPort(), parts, copilot);
            case CursorTransport cursor ->
                new CursorProxyListener(listenHost, gateway.listenPort(), parts, cursor);
            case BufferedTransport buffered ->
                new JdkProxyListener(listenHost, gateway.listenPort(), parts, buffered);
        };
    }

    /**
     * Starts accepting connections on the bound socket.
     */
    public void start() {
        this.listener.start();
    }

    /**
     * The actual bound port, useful when the configured port was {@code 0} (an ephemeral port).
     *
     * @return the port the server is listening on
     */
    public int port() {
        return this.listener.port();
    }

    /**
     * Stops the server with a short grace delay and drains the worker pool.
     */
    public void stop() {
        this.listener.stop(STOP_DELAY_SECONDS);
    }

    void stopImmediately() {
        this.listener.stop(0);
    }
}
