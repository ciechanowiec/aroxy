package eu.ciechanowiec.aroxy.obfuscation;

/**
 * Groups the inputs for a loose literal rule so that {@link RuleCompiler#loose} stays within the
 * parameter budget: the canonical value and whether leet (digit look-alike) matching is enabled.
 */
record LiteralSpec(String value, boolean leet) {

    static LiteralSpec loose(String value) {
        return new LiteralSpec(value, false);
    }

    static LiteralSpec leet(String value) {
        return new LiteralSpec(value, true);
    }
}
