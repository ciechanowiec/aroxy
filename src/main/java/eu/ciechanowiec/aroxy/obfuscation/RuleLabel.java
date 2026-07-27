package eu.ciechanowiec.aroxy.obfuscation;

import eu.ciechanowiec.governance.Justification;
import java.util.Optional;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * The labelling half of a {@link Rule}: the placeholder that names the token, whether the value is
 * sensitive enough to be masked in logs, and the optional canonical spelling that every surface
 * variant collapses onto.
 *
 * <p>When a canonical value is present the vault is keyed by it, so {@code pullman} and
 * {@code Pullman} share one token and deobfuscation restores the canonical spelling. When it is
 * absent (a regex rule) the vault is keyed by the exact matched text.
 */
@Getter
@ToString
@EqualsAndHashCode
@Justification(
    "a Lombok value class, which cannot be a record"
)
@SuppressWarnings(
    "ClassCanBeRecord"
)
final class RuleLabel {

    private final String placeholder;
    private final boolean sensitive;
    private final Optional<String> canonical;

    RuleLabel(String placeholder, boolean sensitive, Optional<String> canonical) {
        this.placeholder = placeholder;
        this.sensitive = sensitive;
        this.canonical = canonical;
    }

    static RuleLabel regex(String placeholder, boolean sensitive) {
        return new RuleLabel(placeholder, sensitive, Optional.empty());
    }

    String vaultKey(String surface) {
        return this.canonical.orElse(surface);
    }
}
