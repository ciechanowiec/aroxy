package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.airness.Justification;
import eu.ciechanowiec.aroxy.client.ClaudeCode;
import eu.ciechanowiec.aroxy.client.ClientProfile;
import eu.ciechanowiec.aroxy.client.Gateway;
import eu.ciechanowiec.aroxy.config.ConfigFile;
import eu.ciechanowiec.aroxy.config.EffectiveConfig;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import eu.ciechanowiec.aroxy.obfuscation.Rule;
import eu.ciechanowiec.aroxy.obfuscation.Vault;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Wires a {@link ProxyServer} bound to port 0 in front of a {@link FakeUpstream}, using a real
 * {@link Obfuscator} compiled from a small rule set: a sensitive AWS-key regex and a non-sensitive
 * literal keyword. Exposes the client-facing base {@link URI} and the {@link AuditLog} so tests can drive the
 * proxy over real sockets and inspect the proof artifacts.
 *
 * <p>The listener serves whichever {@link ClientProfile} it is given, defaulting to
 * {@link ClaudeCode}, so the same harness drives any client's path scope and stream dialect.
 */
final class ProxyHarness {

    static final String SECRET = "AKIAIOSFODNN7EXAMPLE";
    @Justification(
        "kept with its logical owner"
    )
    @SuppressWarnings(
        "StaticMethodOnlyUsedInOneClass"
    )
    static final String KEYWORD = "Helios";
    private static final String RULES = """
        [gateway]
        [rule]
        id = aws_key
        type = regex
        placeholder = AWS_KEY
        sensitive = true
        pattern = (?:AKIA|ASIA)[0-9A-Z]{16}
        [rule]
        id = codename
        type = literal
        placeholder = COMPANY
        value = Helios
        """;

    private final ProxyServer server;
    private final FakeUpstream upstream;

    ProxyHarness(FakeUpstream upstream, Path logsDirectory) throws IOException {
        this(upstream, logsDirectory, ClaudeCode.profile());
    }

    ProxyHarness(FakeUpstream upstream, Path logsDirectory, ClientProfile profile) throws IOException {
        this(upstream, logsDirectory, profile, 0);
    }

    ProxyHarness(
        FakeUpstream upstream, Path logsDirectory, ClientProfile profile, int listenPort
    ) throws IOException {
        this.upstream = upstream;
        Obfuscator obfuscator = obfuscator();
        AuditLog audit = new AuditLog(logsDirectory);
        Gateway gateway = new Gateway(listenPort, upstream.uri());
        ProxyParts parts = new ProxyParts(
            obfuscator, Upstream.forProfile(gateway, profile), audit, profile, logsDirectory
        );
        this.server = new ProxyServer(LocalHost.HOST, gateway, parts);
        this.server.start();
    }

    URI base() {
        return URI.create("http://" + LocalHost.HOST + ":" + this.server.port());
    }

    static Obfuscator obfuscator() {
        List<Rule> rules = EffectiveConfig.merge(List.of(ConfigFile.parse("test", RULES))).rules();
        return new Obfuscator(rules, new Vault(Optional.empty()));
    }

    FakeUpstream upstream() {
        return this.upstream;
    }

    void stop() {
        this.server.stopImmediately();
        this.upstream.stop();
    }
}
