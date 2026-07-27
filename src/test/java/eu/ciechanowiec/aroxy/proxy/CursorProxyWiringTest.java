package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Cursor's upstream must reach what the JDK's clients reach. Translating the selection is pinned by
 * {@link JdkProxySelectionTest}, and this pins the other half: that the answer is wired into the client
 * rather than computed and dropped. Skipping the wiring stays invisible until someone runs aroxy behind
 * an egress proxy, where two of the three upstreams would work and Cursor would fail at connect time,
 * with nothing in the failure naming the cause.
 */
class CursorProxyWiringTest {

    private static final String UPSTREAM = "https://api2.cursor.sh";
    private static final String HOST = "proxy.internal";
    private static final int PORT = 3128;

    @Test
    void dialsThroughTheProxyTheJdkWouldUse() {
        assertEquals(
            1,
            proxiesUnder(fixed(new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(HOST, PORT)))),
            "the selected proxy reaches the client this upstream dials with"
        );
    }

    @Test
    void goesDirectWhenTheJdkWould() {
        assertEquals(0, proxiesUnder(fixed(Proxy.NO_PROXY)), "a direct selection adds no proxy");
    }

    // The default selector is process-wide, so it is restored in a finally rather than after the
    // measurement: a failing assertion must not leave the rest of the suite behind a proxy.
    private static int proxiesUnder(ProxySelector selector) {
        ProxySelector original = ProxySelector.getDefault();
        try {
            ProxySelector.setDefault(selector);
            return count();
        } finally {
            ProxySelector.setDefault(original);
        }
    }

    private static int count() {
        try (CursorDuplexUpstream upstream = new CursorDuplexUpstream(UPSTREAM)) {
            return upstream.proxies().size();
        }
    }

    private static ProxySelector fixed(Proxy proxy) {
        return new ProxySelector() {

            @Override
            public List<Proxy> select(URI target) {
                return List.of(proxy);
            }

            @Override
            public void connectFailed(URI target, SocketAddress address, IOException failure) {
                // the test never dials, so a failed connection has nothing to record
            }
        };
    }
}
