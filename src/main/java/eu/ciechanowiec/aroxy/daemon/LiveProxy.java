package eu.ciechanowiec.aroxy.daemon;

import eu.ciechanowiec.aroxy.config.EffectiveConfig;
import eu.ciechanowiec.aroxy.config.Settings;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import eu.ciechanowiec.aroxy.obfuscation.Rule;
import eu.ciechanowiec.aroxy.obfuscation.Vault;
import eu.ciechanowiec.aroxy.proxy.AuditLog;
import eu.ciechanowiec.aroxy.proxy.ProxyParts;
import eu.ciechanowiec.aroxy.proxy.ProxyServer;
import eu.ciechanowiec.aroxy.proxy.Upstream;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;

/**
 * The running proxy and its swappable obfuscator. The HTTP socket is bound once from the initial
 * settings and stays bound across reloads; only the active rule set is swapped via
 * {@link Obfuscator#swapRules(Collection)}, so a reload never drops a connection or rebinds the port.
 */
final class LiveProxy {

    private final Obfuscator obfuscator;
    private final ProxyServer server;

    private LiveProxy(Obfuscator obfuscator, ProxyServer server) {
        this.obfuscator = obfuscator;
        this.server = server;
    }

    static LiveProxy start(Home home, EffectiveConfig config) throws IOException {
        Settings settings = config.settings();
        Vault vault = new Vault(Optional.of(home.vault()));
        Obfuscator obfuscator = new Obfuscator(config.rules(), vault);
        AuditLog audit = new AuditLog(home.logsDirectory(), settings.maskSecrets());
        Upstream upstream = new Upstream(settings);
        ProxyServer server = new ProxyServer(settings, new ProxyParts(obfuscator, upstream, audit));
        server.start();
        return new LiveProxy(obfuscator, server);
    }

    void reload(Collection<Rule> rules) {
        this.obfuscator.swapRules(rules);
    }

    int port() {
        return this.server.port();
    }

    void stop() {
        this.server.stop();
    }
}
