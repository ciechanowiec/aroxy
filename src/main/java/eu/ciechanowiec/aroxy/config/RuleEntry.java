package eu.ciechanowiec.aroxy.config;

import eu.ciechanowiec.aroxy.obfuscation.RuleSpec;

/**
 * One parsed {@code [rule]} stanza: its declarative {@link RuleSpec} together with the {@code enabled}
 * flag, which lives outside the spec because a disabled rule is dropped before compilation rather
 * than compiled and skipped.
 *
 * @param spec    the declarative description used to compile the rule
 * @param enabled whether the rule contributes to the effective configuration
 */
record RuleEntry(RuleSpec spec, boolean enabled) {

    String id() {
        return this.spec.id();
    }
}
