package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.airness.Justification;
import eu.ciechanowiec.aroxy.client.CopilotTransport;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * Copilot's local forward proxy. The public clear-text connector accepts CONNECT. Protected tunnels
 * terminate on a private TLS connector whose handler can inspect inference HTTP bodies. All other
 * CONNECT destinations remain byte-for-byte tunnels handled by Jetty.
 */
final class CopilotProxyListener implements ProxyListener {

    private static final String LOOPBACK = InetAddress.getLoopbackAddress().getHostAddress();
    private static final String INITIAL_HOST = "api.githubcopilot.com";
    private static final String HTTP_PROTOCOL = "http/1.1";
    private final Server publicServer;
    private final Server privateServer;
    private final ServerConnector publicConnector;
    private final VirtualThreadExchange exchange;

    @Justification("the private server owns and closes its connector")
    @SuppressWarnings("PMD.CloseResource")
    CopilotProxyListener(
        String host, int port, ProxyParts parts, CopilotTransport transport
    ) throws IOException {
        this.privateServer = new Server();
        this.exchange = new VirtualThreadExchange(new CopilotRouter(parts, transport)::serve);
        this.privateServer.setHandler(this.exchange);
        CopilotCertificates certificates = CopilotCertificates.open(parts.stateDirectory());
        SslContextFactory.Server tls = tls(certificates);
        ServerConnector privateConnector = privateConnector(this.privateServer, tls);
        this.privateServer.addConnector(privateConnector);
        privateConnector.open();

        this.publicServer = new Server();
        this.publicConnector = new ServerConnector(this.publicServer);
        this.publicConnector.setHost(host);
        this.publicConnector.setPort(port);
        this.publicServer.addConnector(this.publicConnector);
        InetSocketAddress intercepted = new InetSocketAddress(
            InetAddress.ofLiteral(LOOPBACK), privateConnector.getLocalPort()
        );
        this.publicServer.setHandler(new CopilotTunnel(transport, certificates, tls, intercepted));
        this.publicConnector.open();
    }

    private static SslContextFactory.Server tls(CopilotCertificates certificates) {
        SslContextFactory.Server tls = new SslContextFactory.Server();
        tls.setKeyStore(certificates.ensure(INITIAL_HOST));
        tls.setKeyStorePassword(CopilotCertificates.password());
        tls.setKeyManagerPassword(CopilotCertificates.password());
        tls.setSniRequired(true);
        tls.setIncludeProtocols("TLSv1.3", "TLSv1.2");
        return tls;
    }

    private static ServerConnector privateConnector(Server server, SslContextFactory.Server tls) {
        HttpConfiguration configuration = new HttpConfiguration();
        configuration.addCustomizer(new SecureRequestCustomizer());
        ServerConnector connector = new ServerConnector(
            server,
            new SslConnectionFactory(tls, HTTP_PROTOCOL),
            new HttpConnectionFactory(configuration)
        );
        connector.setHost(LOOPBACK);
        connector.setPort(0);
        return connector;
    }

    @Override
    public void start() {
        LifeCycle.start(this.privateServer);
        LifeCycle.start(this.publicServer);
    }

    @Override
    public int port() {
        return this.publicConnector.getLocalPort();
    }

    @Override
    public void stop(int delaySeconds) {
        long timeout = TimeUnit.SECONDS.toMillis(delaySeconds);
        this.publicServer.setStopTimeout(timeout);
        this.privateServer.setStopTimeout(timeout);
        this.exchange.drain();
        LifeCycle.stop(this.publicServer);
        LifeCycle.stop(this.privateServer);
    }
}
