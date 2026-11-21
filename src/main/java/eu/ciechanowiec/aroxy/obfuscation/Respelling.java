package eu.ciechanowiec.aroxy.obfuscation;

/**
 * The respellings a literal {@link RuleSpec} also matches beyond its verbatim value. Both switches widen
 * one literal to a family of spellings that restore to the same configured value, so they travel
 * together and {@link RuleCompiler} reads them together.
 *
 * @param loose also match respellings with separators between letters
 * @param leet  also match digit look-alikes ({@code o}->{@code 0}, ...)
 */
public record Respelling(boolean loose, boolean leet) {
}
