package eu.ciechanowiec.aroxy.config;

import eu.ciechanowiec.aroxy.client.ClientId;
import eu.ciechanowiec.aroxy.client.ClientProfile;
import eu.ciechanowiec.aroxy.client.ClientRegistry;
import eu.ciechanowiec.aroxy.obfuscation.Rule;
import eu.ciechanowiec.aroxy.obfuscation.RuleCompiler;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The result of merging an ordered list of {@link ConfigFile}s (default first, then persistent, then
 * temporary). {@link Settings} start from {@link Settings#defaults()} and each file's present keys override,
 * last wins. Rules are keyed by {@code id}: a later file re-defining an id replaces it in place while
 * keeping its first-seen position, new ids append, and {@code enabled = false} rules are dropped from
 * the effective list. The contributing-file labels are retained for {@code aroxy config}.
 */
public final class EffectiveConfig {

    private final Settings settings;
    private final List<Rule> rules;
    private final List<String> sources;

    private EffectiveConfig(Settings settings, List<Rule> rules, List<String> sources) {
        this.settings = settings;
        this.rules = rules;
        this.sources = sources;
    }

    /**
     * Merges the given files in order into a single effective configuration.
     *
     * @param files the configuration files to merge, default first then persistent then temporary
     * @return the merged effective configuration
     */
    public static EffectiveConfig merge(Collection<ConfigFile> files) {
        List<String> sources = files.stream().map(ConfigFile::label).toList();
        Settings settings = mergeSettings(files);
        rejectSharedPorts(settings, String.join(", ", sources));
        List<Rule> rules = compile(mergeRules(files));
        return new EffectiveConfig(settings, rules, sources);
    }

    private static Settings mergeSettings(Iterable<ConfigFile> files) {
        Settings merged = Settings.defaults();
        for (ConfigFile file : files) {
            merged = file.gateway().applyTo(merged);
        }
        return merged;
    }

    /**
     * Rejects a merged configuration that points two clients at one port. Each client binds its own
     * listener, so the second bind would fail at startup with an address-already-in-use error naming
     * neither the key nor the client at fault. The clash is a property of the merge rather than of any
     * one file, since one file may set only one of the two keys, which is why it is checked here rather
     * than in {@link GatewayOverlay}.
     */
    private static void rejectSharedPorts(Settings settings, String label) {
        clientsByPort(settings).entrySet().stream()
            .filter(entry -> entry.getValue().size() > 1)
            .findFirst()
            .ifPresent(clash -> rejectClash(label, clash));
    }

    private static Map<Integer, List<String>> clientsByPort(Settings settings) {
        return ClientRegistry.profiles().stream().collect(
            Collectors.groupingBy(
                profile -> settings.gateway(profile.id()).listenPort(),
                Collectors.mapping(ClientProfile::displayName, Collectors.toList())
            )
        );
    }

    private static void rejectClash(String label, Map.Entry<Integer, List<String>> clash) {
        throw new InvalidConfigException(
            label, "port %d is claimed by more than one client: %s"
                .formatted(clash.getKey(), String.join(", ", clash.getValue()))
        );
    }

    private static Map<String, RuleEntry> mergeRules(Iterable<ConfigFile> files) {
        Map<String, RuleEntry> byId = new LinkedHashMap<>();
        files.forEach(file -> file.rules().forEach(entry -> byId.put(entry.id(), entry)));
        return byId;
    }

    private static List<Rule> compile(Map<String, RuleEntry> byId) {
        return byId.values().stream()
            .filter(RuleEntry::enabled)
            .map(RuleEntry::spec)
            .map(RuleCompiler::compile)
            .toList();
    }

    /**
     * Returns a copy in which every client forwards to the given upstream instead of its own fixed
     * one. An upstream is not configurable. This is a test-only seam so an in-process daemon can point
     * at an in-JVM stand-in rather than at a real API.
     *
     * @param upstream the upstream API base address every client forwards to
     * @return a copy of this configuration pointed at the given upstream
     */
    public EffectiveConfig withUpstream(URI upstream) {
        return new EffectiveConfig(this.settings.withUpstreams(upstream), this.rules, this.sources);
    }

    /**
     * Returns a copy in which one client forwards to the given upstream, leaving the others on theirs.
     * A test-only seam, and the one that lets a test prove each listener reaches its own upstream
     * rather than merely that both listeners are bound.
     *
     * @param id       the client whose upstream is repointed
     * @param upstream the upstream API base address that client forwards to
     * @return a copy of this configuration with that client pointed at the given upstream
     */
    public EffectiveConfig withUpstream(ClientId id, URI upstream) {
        Settings pointed = this.settings.withGateway(id, this.settings.gateway(id).withUpstream(upstream));
        return new EffectiveConfig(pointed, this.rules, this.sources);
    }

    /**
     * Returns a copy in which every listener binds a port chosen by the operating system instead of the
     * configured one. A configuration file cannot express this: {@link ConfigValue} rejects port
     * {@code 0} on purpose, because a client needs the stable documented address rather than a port
     * that moves on every restart. This is a test-only seam, so an in-process daemon binds free ports
     * instead of contending for the production ones with a daemon already serving this machine.
     *
     * @return a copy of this configuration bound to ports chosen by the operating system
     */
    public EffectiveConfig withEphemeralPort() {
        return new EffectiveConfig(this.settings.withEphemeralPorts(), this.rules, this.sources);
    }

    /**
     * The merged gateway settings.
     *
     * @return the effective settings
     */
    public Settings settings() {
        return this.settings;
    }

    /**
     * The effective, enabled rule set in application order.
     *
     * @return an immutable copy of the effective rules
     */
    public List<Rule> rules() {
        return List.copyOf(this.rules);
    }

    /**
     * The contributing-file labels, retained for {@code aroxy config}.
     *
     * @return an immutable copy of the contributing-file labels
     */
    public List<String> sources() {
        return List.copyOf(this.sources);
    }
}
