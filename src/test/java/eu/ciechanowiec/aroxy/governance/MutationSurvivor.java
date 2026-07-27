package eu.ciechanowiec.aroxy.governance;

/**
 * One mutant that the test suite did not kill, identified by where it lives and what it changed.
 *
 * <p>The identity deliberately excludes the line number. A line moves whenever anything above it is
 * edited, so a line-keyed record would need regenerating after unrelated changes and would stop being
 * read. The mutator's own description names the call or condition it altered, which distinguishes two
 * mutants in one method without borrowing the fragility of a line.
 *
 * @param owner       the fully qualified class the mutant lives in
 * @param method      the method the mutant lives in
 * @param description what the mutator changed, in PIT's words
 */
record MutationSurvivor(String owner, String method, String description) {

    static MutationSurvivor of(String owner, String method, String description) {
        return new MutationSurvivor(owner.strip(), method.strip(), description.strip());
    }

    /**
     * A rendering for a failure message, short enough to scan a list of.
     *
     * @return the survivor as one readable line
     */
    String readable() {
        return "%s.%s: %s".formatted(
            this.owner.substring(this.owner.lastIndexOf('.') + 1), this.method, this.description
        );
    }
}
