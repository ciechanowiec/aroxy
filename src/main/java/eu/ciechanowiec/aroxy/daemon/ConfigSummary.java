package eu.ciechanowiec.aroxy.daemon;

import eu.ciechanowiec.aroxy.config.EffectiveConfig;
import eu.ciechanowiec.aroxy.config.Settings;
import eu.ciechanowiec.aroxy.obfuscation.Rule;
import java.util.List;
import java.util.stream.Stream;

/**
 * Renders an {@link EffectiveConfig} as a compact, human-readable text block for {@code aroxy config}
 * and {@code GET /config}: the merged settings, the contributing source files, and the ordered ids of
 * the rules that are in effect. No rule values are printed, so the summary stays safe to display.
 */
final class ConfigSummary {

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
            "listen   = " + settings.listenHost() + ":" + settings.listenPort(),
            "upstream = " + settings.upstream(),
            "mask     = " + settings.maskSecrets(),
            "sources  = " + String.join(", ", this.config.sources()),
            "rules    = " + this.config.rules().size()
        );
    }

    private List<String> ruleLines() {
        return this.config.rules().stream().map(ConfigSummary::ruleLine).toList();
    }

    private static String ruleLine(Rule rule) {
        return "  - " + rule.id();
    }
}
