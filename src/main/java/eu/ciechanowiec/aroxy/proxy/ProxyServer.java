package eu.ciechanowiec.aroxy.proxy;

import com.sun.net.httpserver.HttpServer;
import eu.ciechanowiec.aroxy.client.Gateway;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * One client's local listener: a {@link HttpServer} bound to the local host {@code 127.0.0.1} and to
 * that client's {@link Gateway} port (port 0 binds an ephemeral port, exposed via {@link #port()} for
 * tests). Every path is routed to the single {@link ProxyExchange}, which obfuscates the requests in
 * that client's path scope, forwards them through the {@link Upstream}, and deobfuscates the replies.
 *
 * <p>The daemon binds one of these per registered client, which is what lets a single daemon serve
 * several agents while each socket keeps one unambiguous upstream.
 */
public final class ProxyServer {

    private static final int THREADS = 32;
    private static final int STOP_DELAY_SECONDS = 1;
    private final HttpServer server;
    private final ExecutorService executor;

    /**
     * Binds this client's listener to the given host and its gateway port, routing every path to the
     * single exchange.
     *
     * @param listenHost the local host to bind
     * @param gateway    the gateway carrying the port to bind
     * @param parts      the obfuscator, upstream, audit log, and client profile the exchange is wired from
     * @throws IOException if the server socket cannot be bound
     */
    public ProxyServer(String listenHost, Gateway gateway, ProxyParts parts) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(listenHost, gateway.listenPort()), 0);
        this.executor = Executors.newFixedThreadPool(THREADS);
        this.server.createContext("/", new ProxyExchange(parts));
        this.server.setExecutor(this.executor);
    }

    /**
     * Starts accepting connections on the bound socket.
     */
    public void start() {
        this.server.start();
    }

    /**
     * The actual bound port, useful when the configured port was {@code 0} (an ephemeral port).
     *
     * @return the port the server is listening on
     */
    public int port() {
        return this.server.getAddress().getPort();
    }

    /**
     * Stops the server with a short grace delay and drains the worker pool.
     */
    public void stop() {
        this.server.stop(STOP_DELAY_SECONDS);
        this.drain();
    }

    private void drain() {
        this.executor.shutdown();
        if (!awaitTermination(this.executor)) {
            this.executor.shutdownNow();
        }
    }

    private static boolean awaitTermination(ExecutorService executor) {
        try {
            return executor.awaitTermination(STOP_DELAY_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
