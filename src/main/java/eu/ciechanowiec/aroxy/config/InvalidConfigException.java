package eu.ciechanowiec.aroxy.config;

import java.util.Objects;

/**
 * Thrown when a file offered as a configuration file does not follow the stanza grammar - for
 * example a Markdown or prose document. The message names the file and summarizes how many of its
 * meaningful lines are not valid configuration, so the user understands the whole file (not one
 * stray line) is being rejected, instead of the file being silently ingested as an empty config.
 */
public final class InvalidConfigException extends RuntimeException {

    InvalidConfigException(String label, String detail) {
        super("Not a valid aroxy configuration file: " + label + System.lineSeparator() + "  " + detail);
    }

    @Override
    public String getMessage() {
        return Objects.requireNonNull(super.getMessage());
    }
}
