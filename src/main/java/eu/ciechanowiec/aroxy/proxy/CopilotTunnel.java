package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.client.CopilotTransport;
import java.net.InetSocketAddress;
import lombok.SneakyThrows;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ConnectHandler;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * CONNECT router that leaves unrelated HTTPS tunnels opaque and diverts only approved Copilot hosts
 * into aroxy's local TLS listener.
 */
final class CopilotTunnel extends Handler.Abstract {

    private static final String CONNECT = "CONNECT";
    private final CopilotTransport transport;
    private final CopilotCertificates certificates;
    private final SslContextFactory.Server tls;
    private final InetSocketAddress intercepted;
    private final ConnectHandler connector;

    CopilotTunnel(
        CopilotTransport transport,
        CopilotCertificates certificates,
        SslContextFactory.Server tls,
        InetSocketAddress intercepted
    ) {
        this.transport = transport;
        this.certificates = certificates;
        this.tls = tls;
        this.intercepted = intercepted;
        this.connector = new ConnectHandler();
        this.addManaged(this.connector);
    }

    @Override
    public void setServer(Server server) {
        super.setServer(server);
        this.connector.setServer(server);
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        return this.connector.handle(this.route(request), response, callback);
    }

    private Request route(Request request) {
        String host = Request.getServerName(request);
        int port = Request.getServerPort(request);
        InetSocketAddress destination = this.destination(host, port);
        Request routed = request;
        if (destination.equals(this.intercepted)) {
            this.activate(host);
            routed = Request.serveAs(request, HttpURI.from(CONNECT, authority(destination)));
        }
        return routed;
    }

    InetSocketAddress destination(String host, int port) {
        boolean protectedHost = this.transport.intercepts(host, port);
        return protectedHost ? this.intercepted : InetSocketAddress.createUnresolved(host, port);
    }

    @SneakyThrows
    private void activate(String host) {
        this.tls.reload(factory -> factory.setKeyStore(this.certificates.ensure(host)));
    }

    private static String authority(InetSocketAddress address) {
        return address.getHostString() + ":" + address.getPort();
    }
}
