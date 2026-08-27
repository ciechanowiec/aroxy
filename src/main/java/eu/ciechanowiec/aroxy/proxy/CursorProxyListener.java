package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.client.CursorTransport;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.component.LifeCycle;

/**
 * Cursor's dual-protocol listener: HTTP/1.1 and cleartext HTTP/2 share one local port.
 */
final class CursorProxyListener implements ProxyListener {

    private final Server server;
    private final ServerConnector connector;
    private final CursorRouter router;
    private final VirtualThreadExchange exchange;

    CursorProxyListener(
        String host, int port, ProxyParts parts, CursorTransport transport
    ) throws IOException {
        this.server = new Server();
        HttpConfiguration configuration = new HttpConfiguration();
        this.connector = new ServerConnector(
            this.server,
            new HttpConnectionFactory(configuration),
            new HTTP2CServerConnectionFactory(configuration)
        );
        this.connector.setHost(host);
        this.connector.setPort(port);
        this.server.addConnector(this.connector);
        this.router = new CursorRouter(parts, transport);
        this.exchange = new VirtualThreadExchange(this.router::serve);
        this.server.setHandler(this.exchange);
        this.connector.open();
    }

    @Override
    public void start() {
        LifeCycle.start(this.server);
    }

    @Override
    public int port() {
        return this.connector.getLocalPort();
    }

    // Drain, then stop, then close. The exchanges write their audit record and their wire dump after
    // the last byte reaches the agent, so they are given their chance to finish before the server that
    // is carrying them is torn down. Stopping first would cut short exactly what the drain is for.
    @Override
    public void stop(int delaySeconds) {
        this.server.setStopTimeout(TimeUnit.SECONDS.toMillis(delaySeconds));
        this.exchange.drain();
        LifeCycle.stop(this.server);
        this.router.close();
    }
}
