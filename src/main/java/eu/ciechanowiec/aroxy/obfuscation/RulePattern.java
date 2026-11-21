package eu.ciechanowiec.aroxy.obfuscation;

import java.util.regex.Pattern;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * The matching half of a {@link Rule}: the compiled regular expression whose whole match is replaced
 * by a token.
 */
@Getter
@Accessors(
    fluent = true
)
@ToString
@EqualsAndHashCode
@RequiredArgsConstructor
final class RulePattern {

    private final Pattern pattern;
}
