package eu.ciechanowiec.aroxy.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * One parsed configuration file in the stanza/INI format: a {@code [gateway]} overlay carrying only
 * the keys it declares, and an ordered list of {@code [rule]} entries. A line beginning with
 * {@code #} is a comment. {@code [gateway]} or {@code [rule]} opens a stanza. Every other non-blank
 * line is {@code key = value}, split on the first {@code =}. The value is trimmed of surrounding
 * whitespace and otherwise kept as written, so regex patterns need no escaping. A pattern that must
 * match leading or trailing spaces should anchor them (for example with {@code \s}).
 */
public final class ConfigFile {

    private static final char ASSIGN = '=';
    private static final String COMMENT = "#";

    private final String label;
    private final GatewayOverlay gateway;
    private final List<RuleEntry> rules;

    private ConfigFile(String label, StanzaSink sink) {
        this.label = label;
        this.gateway = sink.gateway();
        this.rules = sink.rules();
    }

    /**
     * Parses the UTF-8 file at {@code path}, using the path as the contributing-file label. Rejects a
     * file that does not follow the stanza grammar with an {@link InvalidConfigException}.
     *
     * @param path the configuration file to read and parse
     * @return the parsed configuration file
     */
    public static ConfigFile read(Path path) {
        return parse(path.toString(), readLines(path));
    }

    /**
     * Parses raw stanza text directly, using {@code label} as the contributing-file label. Rejects
     * text that does not follow the stanza grammar with an {@link InvalidConfigException}.
     *
     * @param label the contributing-file label to attribute the parsed content to
     * @param text  the raw stanza text to parse
     * @return the parsed configuration file
     */
    public static ConfigFile parse(String label, String text) {
        return parse(label, text.lines().toList());
    }

    private static ConfigFile parse(String label, List<String> lines) {
        ConfigSyntax.validate(label, lines);
        StanzaSink sink = lex(label, lines);
        Placeholders.validate(label, sink.rules());
        sink.gateway().validate(label);
        return new ConfigFile(label, sink);
    }

    String label() {
        return this.label;
    }

    GatewayOverlay gateway() {
        return this.gateway;
    }

    List<RuleEntry> rules() {
        return this.rules;
    }

    private static StanzaSink lex(String label, Iterable<String> lines) {
        StanzaSink sink = new StanzaSink(label);
        lines.forEach(raw -> consume(sink, raw.strip()));
        return sink;
    }

    private static void consume(StanzaSink sink, String line) {
        boolean ignorable = line.isEmpty() || line.startsWith(COMMENT);
        if (ignorable) {
            return;
        }
        boolean handled = header(sink, line);
        if (!handled) {
            pair(sink, line);
        }
    }

    private static boolean header(StanzaSink sink, String line) {
        boolean isHeader = line.startsWith("[") && line.endsWith("]");
        if (isHeader) {
            sink.header(line.substring(1, line.length() - 1).strip());
        }
        return isHeader;
    }

    private static void pair(StanzaSink sink, String line) {
        int eq = line.indexOf(ASSIGN);
        if (eq < 0) {
            return;
        }
        String key = line.substring(0, eq).strip().toLowerCase(Locale.ROOT);
        String value = line.substring(eq + 1).strip();
        sink.pair(key, value);
    }

    private static List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read config file " + path, exception);
        }
    }
}
