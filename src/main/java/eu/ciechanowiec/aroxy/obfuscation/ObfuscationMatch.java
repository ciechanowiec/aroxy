package eu.ciechanowiec.aroxy.obfuscation;

/**
 * Records one value that was obfuscated: the rule that fired, its placeholder, the original surface
 * text, the token it was replaced with, and whether the value is sensitive (and so must be masked
 * in any log of this match).
 *
 * @param ruleId      the id of the rule that produced this match
 * @param placeholder the rule's placeholder name embedded in the token
 * @param original    the original surface text that was replaced
 * @param token       the {@code <<PII:PLACEHOLDER:N>>} token the original was replaced with
 * @param sensitive   whether the original must be masked whenever this match is logged
 */
public record ObfuscationMatch(String ruleId, String placeholder, String original, String token, boolean sensitive) {

    /**
     * Renders the original for logs, masking it when the value is sensitive.
     */
    String maskedOriginal() {
        return this.sensitive ? MaskedSecret.mask(this.original) : this.original;
    }
}
