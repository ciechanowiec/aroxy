package eu.ciechanowiec.aroxy.proxy;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.eclipse.jetty.client.HttpProxy;

/**
 * Translates the JDK's {@link ProxySelector} decision for one destination into Jetty's proxy
 * configuration.
 *
 * <p>The two HTTP clients aroxy uses do not agree on this by themselves. {@link Upstream} is a
 * {@link java.net.http.HttpClient}, which consults {@link ProxySelector#getDefault()} and therefore
 * honours {@code https.proxyHost} and friends without being asked. Jetty's client starts with an empty
 * proxy configuration and goes direct. Left alone, that means a machine behind an egress proxy would
 * reach two of aroxy's three upstreams and fail on the third, at connect time, with nothing in the
 * failure naming the cause. Reading the same selector here keeps one answer for all of them.
 *
 * <p>Only {@link Proxy.Type#HTTP} is translated. A {@link Proxy.Type#DIRECT} entry means what it says,
 * and Jetty's client has no SOCKS transport to point a {@link Proxy.Type#SOCKS} entry at, so neither
 * adds anything. The first HTTP proxy wins, which is the same one the JDK client would use.
 */
@UtilityClass
final class JdkProxySelection {

    /**
     * The proxy the JDK would use to reach the given destination.
     *
     * @param selector    the selector to consult, normally {@link ProxySelector#getDefault()}
     * @param destination the upstream address the client will connect to
     * @return the equivalent Jetty proxy, empty when the destination is reached directly
     */
    // The address narrowing is a filter rather than a test of its own, because a Proxy that is not
    // DIRECT is constructed with an InetSocketAddress or not at all: the JDK rejects any other address
    // outright. Writing it as a branch here would be writing a branch no input can take.
    static Optional<HttpProxy> proxyFor(ProxySelector selector, URI destination) {
        return selected(selector, destination).stream()
            .filter(proxy -> proxy.type() == Proxy.Type.HTTP)
            .map(Proxy::address)
            .filter(InetSocketAddress.class::isInstance)
            .map(InetSocketAddress.class::cast)
            .map(address -> new HttpProxy(address.getHostString(), address.getPort()))
            .findFirst();
    }

    // A selector is free to reject a destination it cannot classify, and a rejected destination is one
    // more reason to go direct rather than to fail the launch of a listener that has not been used yet.
    private static List<Proxy> selected(ProxySelector selector, URI destination) {
        List<Proxy> proxies = List.of();
        try {
            proxies = List.copyOf(selector.select(destination));
        } catch (IllegalArgumentException _) {
            // An address the selector will not classify is reached directly.
        }
        return proxies;
    }
}
