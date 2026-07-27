package eu.ciechanowiec.aroxy.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Accumulates the parsed stanzas of one configuration file: the merged {@code [gateway]} keys and an
 * ordered list of {@code [rule]} entries. The parser feeds it one stanza header and one
 * {@code key = value} pair at a time. Pairs are routed to the currently open stanza.
 */
final class StanzaSink {

    private static final String GATEWAY = "gateway";
    private static final String RULE = "rule";

    private final String label;
    private final Map<String, String> gatewayKeys;
    private final List<RuleEntry> rules;
    private Optional<String> stanza;
    private Map<String, String> ruleKeys;

    StanzaSink(String label) {
        this.label = label;
        this.gatewayKeys = new LinkedHashMap<>();
        this.rules = new ArrayList<>();
        this.stanza = Optional.empty();
        this.ruleKeys = new LinkedHashMap<>();
    }

    void header(String name) {
        this.flushRule();
        this.stanza = Optional.of(name.toLowerCase(Locale.ROOT));
        this.ruleKeys = new LinkedHashMap<>();
    }

    void pair(String key, String value) {
        this.stanza.ifPresent(name -> this.route(name, key, value));
    }

    private void route(String name, String key, String value) {
        Map<String, String> target = GATEWAY.equals(name) ? this.gatewayKeys : this.ruleKeys;
        target.put(key, value);
    }

    GatewayOverlay gateway() {
        return new GatewayOverlay(this.gatewayKeys);
    }

    List<RuleEntry> rules() {
        this.flushRule();
        return List.copyOf(this.rules);
    }

    private void flushRule() {
        boolean openRule = this.stanza.filter(RULE::equals).isPresent() && !this.ruleKeys.isEmpty();
        if (openRule) {
            new RuleReader(this.label, this.ruleKeys).read().ifPresent(this.rules::add);
            this.ruleKeys = new LinkedHashMap<>();
        }
    }
}
