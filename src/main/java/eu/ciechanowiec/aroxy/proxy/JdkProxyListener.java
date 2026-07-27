package eu.ciechanowiec.aroxy.proxy;

import com.sun.net.httpserver.HttpServer;
import eu.ciechanowiec.aroxy.client.BufferedTransport;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The HTTP/1.1 listener used by buffered agent protocols.
 */
final class JdkProxyListener implements ProxyListener {

    private static final int THREADS = 32;
    private static final int STOP_DELAY_SECONDS = 1;
    private final HttpServer server;
    private final ExecutorService executor;

    JdkProxyListener(
        String host, int port, ProxyParts parts, BufferedTransport transport
    ) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.executor = Executors.newFixedThreadPool(THREADS);
        this.server.createContext("/", new ProxyExchange(parts, transport));
        this.server.setExecutor(this.executor);
    }

    @Override
    public void start() {
        this.server.start();
    }

    @Override
    public int port() {
        return this.server.getAddress().getPort();
    }

    @Override
    public void stop() {
        this.server.stop(STOP_DELAY_SECONDS);
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
