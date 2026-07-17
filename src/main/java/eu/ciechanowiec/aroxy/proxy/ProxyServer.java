package eu.ciechanowiec.aroxy.proxy;

import com.sun.net.httpserver.HttpServer;
import eu.ciechanowiec.aroxy.config.Settings;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The local proxy: a {@link HttpServer} bound to the configured local host {@code 127.0.0.1} and port
 * (port 0 binds an ephemeral port, exposed via {@link #port()} for tests). Every path is routed to the
 * single {@link ProxyExchange}, which obfuscates {@code /v1/messages} requests, forwards them through the
 * {@link Upstream}, and deobfuscates the replies.
 */
public final class ProxyServer {

    private static final int THREADS = 32;
    private static final int STOP_DELAY_SECONDS = 1;
    private final HttpServer server;
    private final ExecutorService executor;

    /**
     * Binds the proxy to the configured host and port and routes every path to the single exchange.
     *
     * @param settings the gateway settings carrying the listen host and port
     * @param parts    the obfuscator, upstream, and audit log the exchange is wired from
     * @throws IOException if the server socket cannot be bound
     */
    public ProxyServer(Settings settings, ProxyParts parts) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(settings.listenHost(), settings.listenPort()), 0);
        this.executor = Executors.newFixedThreadPool(THREADS);
        this.server.createContext("/", new ProxyExchange(parts.obfuscator(), parts.upstream(), parts.audit()));
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
