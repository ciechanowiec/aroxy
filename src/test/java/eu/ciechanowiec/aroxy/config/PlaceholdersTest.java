package eu.ciechanowiec.aroxy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * Locks the closed placeholder vocabulary: a config naming a placeholder absent from
 * {@code default.conf} is rejected as broken, and the hard-coded allow-list never drifts from the names
 * actually shipped in {@code default.conf} nor from the list the README gives its readers. The README is
 * where a user learns which names exist, so a list that drifts there is as broken as one that drifts in
 * the code.
 */
class PlaceholdersTest {

    private static final String PREFIX = "placeholder";
    private static final char ASSIGN = '=';
    private static final Pattern README_LIST = Pattern.compile("^`([A-Z_]+(?:`, `[A-Z_]+)+)`\\.$");

    @Test
    void rejectsAConfigUsingAnUnenumeratedPlaceholder() {
        String text = "[rule]\nid = x\ntype = literal\nplaceholder = COMPANY_D\nvalue = Acme\n";
        InvalidConfigException rejected = assertThrows(
            InvalidConfigException.class,
            () -> ConfigFile.parse("custom", text)
        );
        assertTrue(rejected.getMessage().contains("COMPANY_D"), rejected.getMessage());
        assertTrue(rejected.getMessage().contains("allowed placeholder"), rejected.getMessage());
    }

    @Test
    void acceptsAConfigUsingAnEnumeratedPlaceholder() {
        String text = "[rule]\nid = x\ntype = literal\nplaceholder = COMPANY\nvalue = Acme\n";
        assertEquals(1, ConfigFile.parse("custom", text).rules().size(), "an enumerated placeholder is accepted");
    }

    @Test
    void allowListMatchesBundledDefaultConfig() {
        assertEquals(
            bundledPlaceholders(), Placeholders.allowed(),
            "the allow-list must stay in step with the placeholders shipped in default.conf"
        );
    }

    @Test
    void allowListMatchesTheListTheReadmeGivesItsReaders() {
        assertEquals(
            Placeholders.allowed(), documentedPlaceholders(),
            "the README names the placeholders a user may write, so it must name exactly these"
        );
    }

    @SneakyThrows
    private static Set<String> documentedPlaceholders() {
        return Files.readAllLines(Path.of("README.adoc")).stream()
            .map(String::strip)
            .map(README_LIST::matcher)
            .filter(Matcher::matches)
            .map(matcher -> matcher.group(1))
            .flatMap(list -> Stream.of(list.split("`, `")))
            .collect(Collectors.toSet());
    }

    private static Set<String> bundledPlaceholders() {
        return DefaultConfigFixture.bundledText().lines()
            .map(String::strip)
            .filter(PlaceholdersTest::isPlaceholderLine)
            .map(PlaceholdersTest::valueOf)
            .collect(Collectors.toSet());
    }

    private static boolean isPlaceholderLine(String line) {
        return line.startsWith(PREFIX) && line.indexOf(ASSIGN) > 0;
    }

    private static String valueOf(String line) {
        return line.substring(line.indexOf(ASSIGN) + 1).strip();
    }
}
