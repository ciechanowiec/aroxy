package eu.ciechanowiec.aroxy.config;

import eu.ciechanowiec.aroxy.obfuscation.Rule;
import eu.ciechanowiec.aroxy.obfuscation.RuleCompiler;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The result of merging an ordered list of {@link ConfigFile}s (default first, then persistent, then
 * temporary). {@link Settings} start from {@link Settings#defaults()} and each file's present keys override,
 * last wins. Rules are keyed by {@code id}: a later file re-defining an id replaces it in place while
 * keeping its first-seen position, new ids append, and {@code enabled = false} rules are dropped from
 * the effective list. The contributing-file labels are retained for {@code aroxy config}.
 */
public final class EffectiveConfig {

    private static final int EPHEMERAL_PORT = 0;

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
        Settings settings = mergeSettings(files);
        List<Rule> rules = compile(mergeRules(files));
        List<String> sources = files.stream().map(ConfigFile::label).toList();
        return new EffectiveConfig(settings, rules, sources);
    }

    private static Settings mergeSettings(Iterable<ConfigFile> files) {
        Settings merged = Settings.defaults();
        for (ConfigFile file : files) {
            merged = file.gateway().applyTo(merged);
        }
        return merged;
    }

    @SuppressWarnings(
        "PMD.UseConcurrentHashMap"
    ) // insertion order is the merge contract; access is single-threaded
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
     * Returns a copy that forwards to the given upstream instead of the fixed
     * {@code https://api.anthropic.com}. The upstream is not configurable; this is a test-only seam so
     * a {@link Settings} can point an in-process daemon at an in-JVM
     * stand-in for the Claude API.
     *
     * @param upstream the upstream API base address to forward to
     * @return a copy of this configuration pointed at the given upstream
     */
    public EffectiveConfig withUpstream(URI upstream) {
        return new EffectiveConfig(this.settings.withUpstream(upstream), this.rules, this.sources);
    }

    /**
     * Returns a copy that binds a port chosen by the operating system instead of the configured one.
     * A configuration file cannot express this: {@link ConfigValue} rejects port {@code 0} on purpose,
     * because Claude Code needs the stable documented address rather than a port that moves on every
     * restart. This is a test-only seam, so an in-process daemon binds a free port instead of
     * contending for the production one with a daemon already serving this machine.
     *
     * @return a copy of this configuration bound to a port chosen by the operating system
     */
    public EffectiveConfig withEphemeralPort() {
        return new EffectiveConfig(this.settings.withPort(EPHEMERAL_PORT), this.rules, this.sources);
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
