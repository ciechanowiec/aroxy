package eu.ciechanowiec.aroxy.obfuscation;

import java.util.regex.Pattern;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * The matching half of a {@link Rule}: the compiled regular expression whose whole match is replaced
 * by a token.
 */
@Getter
@ToString
@EqualsAndHashCode
@RequiredArgsConstructor
// Lombok value class; cannot be a record
@SuppressWarnings(
    "ClassCanBeRecord"
)
final class RulePattern {

    private final Pattern pattern;
}
