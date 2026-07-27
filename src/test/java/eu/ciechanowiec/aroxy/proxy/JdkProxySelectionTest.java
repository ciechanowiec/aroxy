package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.eclipse.jetty.client.HttpProxy;
import org.junit.jupiter.api.Test;

/**
 * Jetty's client starts with an empty proxy configuration while the JDK's client reads
 * {@link ProxySelector}, so a machine behind an egress proxy would reach two of aroxy's upstreams and
 * fail on the third. These pin the translation that keeps one answer for both clients.
 */
class JdkProxySelectionTest {

    private static final URI UPSTREAM = URI.create("https://api2.cursor.sh");
    private static final String HOST = "proxy.internal";
    private static final int PORT = 3128;

    @Test
    void translatesTheProxyTheJdkWouldUse() {
        Optional<HttpProxy> proxy = JdkProxySelection.proxyFor(
            fixed(new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(HOST, PORT))), UPSTREAM
        );
        assertEquals(HOST, proxy.orElseThrow().getURI().getHost());
        assertEquals(PORT, proxy.orElseThrow().getURI().getPort());
    }

    @Test
    void readsNoProxyFromADirectSelection() {
        assertTrue(JdkProxySelection.proxyFor(fixed(Proxy.NO_PROXY), UPSTREAM).isEmpty());
    }

    // Jetty's client has no SOCKS transport, so translating a SOCKS entry would produce a proxy that
    // cannot be dialled. Going direct is the honest outcome, and matches what the client did before.
    @Test
    void ignoresAProxyItCannotDial() {
        Optional<HttpProxy> proxy = JdkProxySelection.proxyFor(
            fixed(new Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(HOST, PORT))), UPSTREAM
        );
        assertTrue(proxy.isEmpty());
    }

    @Test
    void takesTheFirstDiallableProxyOfSeveral() {
        ProxySelector selector = selector(
            List.of(
                new Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("socks.internal", PORT)),
                new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(HOST, PORT)),
                new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("second.internal", PORT))
            )
        );
        assertEquals(HOST, JdkProxySelection.proxyFor(selector, UPSTREAM).orElseThrow().getURI().getHost());
    }

    // A selector may reject a destination it will not classify. Failing the listener over that would
    // take down a client that has not been used yet, so an unclassifiable destination is reached
    // directly instead.
    @Test
    void goesDirectWhenTheSelectorRejectsTheDestination() {
        ProxySelector selector = new ProxySelector() {

            @Override
            public List<Proxy> select(URI target) {
                throw new IllegalArgumentException("unclassifiable: " + target);
            }

            @Override
            public void connectFailed(URI target, SocketAddress address, IOException failure) {
                // no bookkeeping is kept for a selector that classifies nothing
            }
        };
        assertTrue(JdkProxySelection.proxyFor(selector, UPSTREAM).isEmpty());
    }

    private static ProxySelector fixed(Proxy proxy) {
        return selector(List.of(proxy));
    }

    private static ProxySelector selector(Collection<Proxy> proxies) {
        return new ProxySelector() {

            @Override
            public List<Proxy> select(URI target) {
                return List.copyOf(proxies);
            }

            @Override
            public void connectFailed(URI target, SocketAddress address, IOException failure) {
                // the tests never dial, so a failed connection has nothing to record
            }
        };
    }
}
