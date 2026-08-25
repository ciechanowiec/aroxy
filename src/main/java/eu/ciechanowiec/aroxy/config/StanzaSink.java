package eu.ciechanowiec.aroxy.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

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
    /**
     * The stanza currently open, held in a reference rather than a reassigned field. Empty until the
     * first header, and replaced wholesale by each header after it.
     */
    private final AtomicReference<Optional<String>> stanza;

    /**
     * The pairs of the rule stanza being read. Cleared rather than replaced between stanzas, which is
     * safe because {@link RuleReader} copies what it is handed before this sink moves on.
     */
    private final Map<String, String> ruleKeys;

    StanzaSink(String label) {
        this.label = label;
        this.gatewayKeys = new LinkedHashMap<>();
        this.rules = new ArrayList<>();
        this.stanza = new AtomicReference<>(Optional.empty());
        this.ruleKeys = new LinkedHashMap<>();
    }

    void header(String name) {
        this.flushRule();
        this.stanza.set(Optional.of(name.toLowerCase(Locale.ROOT)));
        this.ruleKeys.clear();
    }

    void pair(String key, String value) {
        this.open().ifPresent(name -> this.route(name, key, value));
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
        boolean openRule = this.open().filter(RULE::equals).isPresent() && !this.ruleKeys.isEmpty();
        if (openRule) {
            new RuleReader(this.label, this.ruleKeys).read().ifPresent(this.rules::add);
            this.ruleKeys.clear();
        }
    }

    private Optional<String> open() {
        return Objects.requireNonNullElse(this.stanza.get(), Optional.empty());
    }
}
