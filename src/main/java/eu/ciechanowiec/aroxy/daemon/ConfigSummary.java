package eu.ciechanowiec.aroxy.daemon;

import eu.ciechanowiec.aroxy.client.ClientProfile;
import eu.ciechanowiec.aroxy.client.ClientRegistry;
import eu.ciechanowiec.aroxy.client.Gateway;
import eu.ciechanowiec.aroxy.config.EffectiveConfig;
import eu.ciechanowiec.aroxy.config.Settings;
import eu.ciechanowiec.aroxy.obfuscation.Rule;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Renders an {@link EffectiveConfig} as a compact, human-readable text block for {@code aroxy config}
 * and {@code GET /config}: the merged settings, the contributing source files, and the ordered ids of
 * the rules that are in effect. No rule values are printed, so the summary stays safe to display.
 */
final class ConfigSummary {

    private static final String INDENT = "  ";
    private static final Map<Boolean, String> MASKING = Map.of(true, "enabled", false, "disabled");

    private final EffectiveConfig config;

    ConfigSummary(EffectiveConfig config) {
        this.config = config;
    }

    String render() {
        return String.join("\n", this.lines()) + "\n";
    }

    private List<String> lines() {
        Settings settings = this.config.settings();
        return Stream.concat(this.header(settings).stream(), this.ruleLines().stream()).toList();
    }

    private List<String> header(Settings settings) {
        return List.of(
            INDENT + "Secret masking  " + MASKING.get(settings.maskSecrets()),
            INDENT + "Listeners",
            INDENT.repeat(2) + GatewayListing.render(configured(settings)).replace("\n", "\n" + INDENT.repeat(2)),
            INDENT + "Sources         " + String.join(", ", this.config.sources()),
            INDENT + "Rules           " + this.config.rules().size()
        );
    }

    /**
     * The listeners this configuration would bind, in registration order. Read from the registry rather
     * than from a fixed list, so a newly registered client appears here without an edit.
     */
    private static List<GatewayAddress> configured(Settings settings) {
        return ClientRegistry.profiles().stream()
            .map(profile -> address(settings, profile))
            .toList();
    }

    private static GatewayAddress address(Settings settings, ClientProfile profile) {
        Gateway gateway = settings.gateway(profile.id());
        return new GatewayAddress(
            profile.displayName(), settings.listenHost() + ":" + gateway.listenPort(), gateway.upstream().toString()
        );
    }

    private List<String> ruleLines() {
        return this.config.rules().stream().map(ConfigSummary::ruleLine).toList();
    }

    private static String ruleLine(Rule rule) {
        return INDENT.repeat(2) + "• " + rule.id();
    }
}
