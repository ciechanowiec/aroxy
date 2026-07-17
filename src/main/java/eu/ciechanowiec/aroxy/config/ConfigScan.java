package eu.ciechanowiec.aroxy.config;

/**
 * A running tally of one configuration file's lines as {@link ConfigSyntax} scans it: whether a
 * stanza is open, how many meaningful (non-blank, non-comment) lines were seen, how many of those
 * are not valid configuration, and the first such offender. It becomes an
 * {@link InvalidConfigException} only when at least one offending line was found, so the message can
 * say how much of the file is non-config rather than blaming a single line.
 */
final class ConfigScan {

    private boolean stanzaOpen;
    private int meaningful;
    private int offending;
    private int firstOffendingLine;
    private String firstOffendingText;

    ConfigScan() {
        this.stanzaOpen = false;
        this.meaningful = 0;
        this.offending = 0;
        this.firstOffendingLine = 0;
        this.firstOffendingText = "";
    }

    void openStanza(boolean known) {
        this.stanzaOpen = this.stanzaOpen || known;
    }

    boolean stanzaOpen() {
        return this.stanzaOpen;
    }

    void note(int lineNumber, String text, boolean ignorable, boolean conforming) {
        this.meaningful += ignorable ? 0 : 1;
        this.tally(lineNumber, text, !ignorable && !conforming);
    }

    void rejectIfInvalid(String label) {
        if (this.offending > 0) {
            throw new InvalidConfigException(label, this.summary());
        }
    }

    private void tally(int lineNumber, String text, boolean isOffending) {
        boolean first = isOffending && this.offending == 0;
        this.offending += isOffending ? 1 : 0;
        this.firstOffendingLine = first ? lineNumber : this.firstOffendingLine;
        this.firstOffendingText = first ? text : this.firstOffendingText;
    }

    private String summary() {
        return this.offending + " of " + this.meaningful
            + " meaningful line(s) are not valid configuration; first at line "
            + this.firstOffendingLine + ": " + this.firstOffendingText;
    }
}
