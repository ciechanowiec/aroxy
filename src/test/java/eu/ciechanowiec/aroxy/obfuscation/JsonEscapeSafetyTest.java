package eu.ciechanowiec.aroxy.obfuscation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.stream.Stream;
import org.json.JSONObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Guards the backslash-snap behaviour: when a match sits next to a JSON escape, the obfuscated body must still parse as
 * valid JSON and the secret must still be gone. Parsing is verified for real with {@code org.json}, not a stub.
 */
class JsonEscapeSafetyTest {

    private static final String SECRET_WORD = "abcdefghij";
    private static final String GENERIC = "(?i)(password|passwd|secret|api[_-]?key|token)\\s*[=:]\\s*([^\\s\"']{6,})";

    private static String wrap(String content) {
        return "{\"messages\":[{\"role\":\"user\",\"content\":\"" + content + "\"}]}";
    }

    static Stream<Arguments> cases() {
        return Stream.of(
            Arguments.of(
                RuleFixtures.regex("word", "W", SECRET_WORD),
                wrap("token: " + SECRET_WORD + "\\\" tail"), SECRET_WORD
            ),
            Arguments.of(
                RuleFixtures.regex("generic_secret", "S", GENERIC),
                wrap("secret=hunter2longvalue\\\" tail"), "hunter2longvalue"
            ),
            Arguments.of(
                RuleFixtures.regex(
                    "dsn", "DSN",
                    "[a-z][a-z0-9+.-]*://[^\\s:@/\"]+:[^\\s:@/\"]+@[^\\s/\"]+"
                ),
                wrap("dsn postgres://app:s3cr3tPass@db.internal:5432/orders before \\\" end"),
                "s3cr3tPass"
            ),
            Arguments.of(
                RuleFixtures.regex("trailing", "T", "value=[A-Za-z]+"),
                wrap("value=secretword\\\\ windows"), "secretword"
            ),
            Arguments.of(
                RuleFixtures.regex("winpath", "P", "C:[A-Za-z\\\\]+"),
                wrap("path C:\\\\Users\\\\admin\\\\secret end"), "Users"
            )
        );
    }

    @ParameterizedTest
    @MethodSource(
        "cases"
    )
    void obfuscatedBodyStaysValidJson(Rule rule, String input, CharSequence leaked) {
        Obfuscator obfuscator = RuleFixtures.obfuscator(rule);
        ObfuscationResult obfuscated = obfuscator.obfuscate(input);
        assertDoesNotThrow(() -> new JSONObject(obfuscated.text()), "obfuscated body must be valid JSON");
        assertFalse(obfuscated.text().contains(leaked), "secret value must not leak");
    }

    @ParameterizedTest
    @MethodSource(
        "cases"
    )
    void inputItselfIsValidJson(Rule rule, String input, CharSequence leaked) {
        assertDoesNotThrow(() -> new JSONObject(input), "test fixture must start as valid JSON");
        assertFalse(rule.id().isEmpty(), "rule present");
        assertFalse(leaked.isEmpty(), "leaked marker present");
    }
}
