package eu.ciechanowiec.aroxy.proxy;

import java.net.InetAddress;
import lombok.experimental.UtilityClass;

/**
 * The local host address {@code 127.0.0.1}, resolved at runtime so the proxy tests bind and dial the
 * local interface without hard-coding a literal IP.
 */
@UtilityClass
final class LocalHost {

    static final String HOST = InetAddress.getLoopbackAddress().getHostAddress();
}
