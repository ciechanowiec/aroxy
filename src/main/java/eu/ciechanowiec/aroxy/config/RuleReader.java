package eu.ciechanowiec.aroxy.config;

import eu.ciechanowiec.aroxy.obfuscation.Respelling;
import eu.ciechanowiec.aroxy.obfuscation.RuleKind;
import eu.ciechanowiec.aroxy.obfuscation.RuleSpec;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns the {@code key = value} pairs of one {@code [rule]} stanza into a {@link RuleEntry}. A stanza
 * missing an {@code id}, a {@code placeholder}, or its {@code pattern}/{@code value} source declares no
 * rule and is skipped, so an unfinished stanza never aborts the whole file. A key it does declare must
 * be usable: a key this stanza does not define, or a value the reader cannot read, breaks the file.
 */
final class RuleReader {

    private static final String ID = "id";
    private static final String ENABLED = "enabled";
    private static final String TYPE = "type";
    private static final String PLACEHOLDER = "placeholder";
    private static final String SENSITIVE = "sensitive";
    private static final String FLAGS = "flags";
    private static final String LOOSE = "loose";
    private static final String LEET = "leet";
    private static final String PATTERN = "pattern";
    private static final String VALUE = "value";
    private static final String LITERAL = "literal";
    private static final String REGEX = "regex";
    private static final Map<String, RuleKind> KINDS = Map.of(
        LITERAL, RuleKind.LITERAL, REGEX, RuleKind.REGEX
    );
    private static final Set<String> KNOWN = Set.of(
        ID, ENABLED, TYPE, PLACEHOLDER, SENSITIVE, FLAGS, LOOSE, LEET, PATTERN, VALUE
    );

    private final String label;
    private final Map<String, String> keys;

    RuleReader(String label, Map<String, String> keys) {
        this.label = label;
        this.keys = Map.copyOf(keys);
    }

    Optional<RuleEntry> read() {
        this.rejectUnknownKey();
        Optional<String> id = this.presentValue(ID);
        Optional<String> placeholder = this.presentValue(PLACEHOLDER);
        return id.flatMap(
            presentId -> placeholder.flatMap(presentPlaceholder -> this.build(presentId, presentPlaceholder))
        );
    }

    private void rejectUnknownKey() {
        this.keys.keySet().stream()
            .filter(key -> !KNOWN.contains(key))
            .findFirst()
            .ifPresent(this::rejectKey);
    }

    private void rejectKey(String key) {
        throw new InvalidConfigException(
            this.label, "'" + key + "' is not a [rule] key; choose one of: " + known()
        );
    }

    private static String known() {
        return KNOWN.stream().sorted().collect(Collectors.joining(", "));
    }

    private Optional<RuleEntry> build(String id, String placeholder) {
        RuleKind kind = this.kind();
        return this.source(kind).map(source -> this.entry(id, placeholder, kind, source));
    }

    private RuleEntry entry(String id, String placeholder, RuleKind kind, String source) {
        Respelling respelling = new Respelling(this.boolValue(LOOSE, false), this.boolValue(LEET, false));
        RuleSpec spec = new RuleSpec(
            id, kind, placeholder, source, this.flags(), this.boolValue(SENSITIVE, false), respelling
        );
        return new RuleEntry(spec, this.boolValue(ENABLED, true));
    }

    private RuleKind kind() {
        return this.valueOf(TYPE).map(this::requireKind).orElse(RuleKind.REGEX);
    }

    private RuleKind requireKind(String raw) {
        return Optional.ofNullable(KINDS.get(raw.toLowerCase(Locale.ROOT)))
            .orElseThrow(() -> this.rejection(TYPE, raw, LITERAL + " or " + REGEX));
    }

    private Optional<String> source(RuleKind kind) {
        String key = kind == RuleKind.LITERAL ? VALUE : PATTERN;
        return this.valueOf(key).filter(source -> !source.isBlank());
    }

    private String flags() {
        return this.valueOf(FLAGS).map(raw -> raw.toLowerCase(Locale.ROOT)).orElse("");
    }

    private boolean boolValue(String key, boolean whenAbsent) {
        return this.valueOf(key).map(raw -> this.requireBoolean(key, raw)).orElse(whenAbsent);
    }

    private boolean requireBoolean(String key, String raw) {
        return ConfigValue.asBoolean(raw)
            .orElseThrow(() -> this.rejection(key, raw, ConfigValue.booleanValues()));
    }

    private InvalidConfigException rejection(String key, String raw, String expected) {
        return new InvalidConfigException(this.label, "%s '%s' is not %s".formatted(key, raw, expected));
    }

    private Optional<String> presentValue(String key) {
        return this.valueOf(key).filter(value -> !value.isEmpty());
    }

    private Optional<String> valueOf(String key) {
        return Optional.ofNullable(this.keys.get(key));
    }
}
