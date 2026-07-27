package eu.ciechanowiec.aroxy.daemon;

import eu.ciechanowiec.aroxy.client.ClientId;
import eu.ciechanowiec.aroxy.client.ClientProfile;
import eu.ciechanowiec.aroxy.client.ClientRegistry;
import eu.ciechanowiec.aroxy.client.Gateway;
import eu.ciechanowiec.aroxy.config.EffectiveConfig;
import eu.ciechanowiec.aroxy.config.Settings;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import eu.ciechanowiec.aroxy.obfuscation.Vault;
import eu.ciechanowiec.aroxy.proxy.AuditLog;
import eu.ciechanowiec.aroxy.proxy.ProxyParts;
import eu.ciechanowiec.aroxy.proxy.ProxyServer;
import eu.ciechanowiec.aroxy.proxy.Upstream;
import eu.ciechanowiec.governance.Justification;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The running proxy and its swappable obfuscator: one {@link ProxyServer} per registered
 * {@link ClientProfile}, each bound to that client's port and forwarding to that client's upstream.
 *
 * <p>One {@link Obfuscator}, one {@link Vault}, and one {@link AuditLog} are shared across every
 * listener, so a secret carries the same token whichever agent sends it and one audit trail covers the
 * machine. Only the {@link Upstream} and the profile differ per listener.
 *
 * <p>The sockets are bound once from the initial settings and stay bound across reloads. Rules and
 * audit masking are updated in place, so a reload never drops a connection or rebinds a port, and
 * every listener reloads together.
 */
@Justification(
    "composes the whole proxy stack once per client, and coupling is governed by the project's own Checkstyle cap"
)
@SuppressWarnings(
    "ClassWithTooManyDependencies"
)
final class LiveProxy {

    private final Obfuscator obfuscator;
    private final AuditLog audit;
    private final Map<ClientId, ProxyServer> servers;

    private LiveProxy(Obfuscator obfuscator, AuditLog audit, Map<ClientId, ProxyServer> servers) {
        this.obfuscator = obfuscator;
        this.audit = audit;
        this.servers = servers;
    }

    static LiveProxy start(Home home, EffectiveConfig config) throws IOException {
        Settings settings = config.settings();
        Vault vault = new Vault(Optional.of(home.vault()));
        Obfuscator obfuscator = new Obfuscator(config.rules(), vault);
        AuditLog audit = new AuditLog(home.logsDirectory(), settings.maskSecrets());
        Map<ClientId, ProxyServer> servers = bindAll(settings, obfuscator, audit);
        servers.values().forEach(ProxyServer::start);
        return new LiveProxy(obfuscator, audit, servers);
    }

    /**
     * Binds every registered client's listener, releasing the ones already bound if a later one cannot
     * bind. Without that, a port already taken by another process would leave this daemon holding
     * sockets it will never serve.
     */
    @Justification(
        "built once on the start path and only read afterwards"
    )
    @SuppressWarnings(
        "PMD.UseConcurrentHashMap"
    )
    private static Map<ClientId, ProxyServer> bindAll(
        Settings settings, Obfuscator obfuscator, AuditLog audit
    ) throws IOException {
        Map<ClientId, ProxyServer> servers = new LinkedHashMap<>();
        try {
            bindInto(servers, settings, obfuscator, audit);
        } catch (IOException exception) {
            servers.values().forEach(ProxyServer::stop);
            throw exception;
        }
        return servers;
    }

    private static void bindInto(
        Map<ClientId, ProxyServer> servers, Settings settings, Obfuscator obfuscator, AuditLog audit
    ) throws IOException {
        for (ClientProfile profile : ClientRegistry.profiles()) {
            servers.put(profile.id(), bind(settings, profile, obfuscator, audit));
        }
    }

    private static ProxyServer bind(
        Settings settings, ClientProfile profile, Obfuscator obfuscator, AuditLog audit
    ) throws IOException {
        Gateway gateway = settings.gateway(profile.id());
        ProxyParts parts = new ProxyParts(obfuscator, new Upstream(gateway), audit, profile);
        return new ProxyServer(settings.listenHost(), gateway, parts);
    }

    void reload(EffectiveConfig config) {
        this.obfuscator.swapRules(config.rules());
        this.audit.setMaskSecrets(config.settings().maskSecrets());
    }

    /**
     * The listeners as they were actually bound, in client declaration order. The port is read from the
     * live server rather than from the settings, so a port the operating system chose is reported
     * rather than the {@code 0} that asked for one.
     *
     * @param settings the settings carrying the listen host and each client's upstream
     * @return one address per bound listener
     */
    List<GatewayAddress> addresses(Settings settings) {
        return this.servers.entrySet().stream()
            .map(entry -> address(settings, profile(entry.getKey()), entry.getValue().port()))
            .toList();
    }

    private static ClientProfile profile(ClientId id) {
        return ClientRegistry.profiles().stream()
            .filter(candidate -> candidate.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No client profile is registered for " + id));
    }

    private static GatewayAddress address(Settings settings, ClientProfile profile, int port) {
        return new GatewayAddress(
            profile.displayName(), settings.listenHost() + ":" + port,
            settings.gateway(profile.id()).upstream().toString()
        );
    }

    void stop() {
        this.servers.values().forEach(ProxyServer::stop);
    }
}
