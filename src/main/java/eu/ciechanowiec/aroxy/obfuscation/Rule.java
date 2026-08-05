package eu.ciechanowiec.aroxy.obfuscation;

import eu.ciechanowiec.airness.Justification;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * One obfuscation rule: an identifier, an enabled flag, and the two composed value objects that
 * carry its matching behaviour ({@link RulePattern}) and its labelling behaviour ({@link RuleLabel}).
 * Configuration order breaks ties between matches with the same start and length.
 */
@Getter
@Accessors(
    fluent = true
)
@ToString
@EqualsAndHashCode
@RequiredArgsConstructor
@Justification(
    "a Lombok value class, which cannot be a record"
)
@SuppressWarnings(
    "ClassCanBeRecord"
)
public final class Rule {

    private final String id;
    private final boolean enabled;
    private final RulePattern rulePattern;
    private final RuleLabel ruleLabel;

    static Rule enabled(String id, RulePattern rulePattern, RuleLabel ruleLabel) {
        return new Rule(id, true, rulePattern, ruleLabel);
    }
}
