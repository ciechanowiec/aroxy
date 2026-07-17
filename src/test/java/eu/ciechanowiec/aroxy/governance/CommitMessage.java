package eu.ciechanowiec.aroxy.governance;

/**
 * A commit message split into its header (the first line) and its body (everything after the first
 * blank line), both already stripped of surrounding whitespace.
 */
record CommitMessage(String header, String body) {
}
