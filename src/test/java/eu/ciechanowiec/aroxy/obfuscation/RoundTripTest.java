package eu.ciechanowiec.aroxy.obfuscation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Round-trips every regex rule type: the secret is gone from the obfuscated text, a token is present, and deobfuscation
 * restores the input exactly.
 */
class RoundTripTest {

    private static final String PREFIX = "{\"messages\":[{\"role\":\"user\",\"content\":\"here it is: ";
    private static final String SUFFIX = " ok\"}]}";

    private static String body(String secret) {
        return PREFIX + secret + SUFFIX;
    }

    static Stream<Arguments> regexCases() {
        return Stream.of(
            Arguments.of(
                RuleFixtures.regex(
                    "private_key", "PRIVKEY",
                    "-----BEGIN PRIVATE KEY-----[\\s\\S]*?-----END PRIVATE KEY-----"
                ),
                "-----BEGIN PRIVATE KEY-----MIIEvQIBADANBgkqh-----END PRIVATE KEY-----"
            ),
            Arguments.of(
                RuleFixtures.regex("aws_access_key", "AWS_KEY", "(?:AKIA|ASIA)[0-9A-Z]{16}"),
                "AKIAIOSFODNN7EXAMPLE"
            ),
            Arguments.of(
                RuleFixtures.regex("github_token", "GH_TOKEN", "gh[posru]_[A-Za-z0-9]{36,}"),
                "ghp_1234567890abcdefghijklmnopqrstuvwxyz"
            ),
            Arguments.of(
                RuleFixtures.regex(
                    "jwt", "JWT",
                    "eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"
                ),
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMiJ9.dQw4w9WgXcQabcdefghijklmnop"
            ),
            Arguments.of(
                RuleFixtures.regex(
                    "connection_string", "DSN",
                    "[a-z][a-z0-9+.-]*://[^\\s:@/\"]+:[^\\s:@/\"]+@[^\\s/\"]+"
                ),
                "postgres://app:s3cr3tPass@db.internal:5432/orders"
            ),
            Arguments.of(
                RuleFixtures.regex(
                    "email", "EMAIL",
                    "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
                ),
                "jane.doe@acme.com"
            )
        );
    }

    @ParameterizedTest
    @MethodSource(
        "regexCases"
    )
    void roundTripsRegexRule(Rule rule, String secret) {
        Obfuscator obfuscator = RuleFixtures.obfuscator(rule);
        String input = body(secret);
        ObfuscationResult obfuscated = obfuscator.obfuscate(input);
        assertFalse(obfuscated.text().contains(secret), "secret must be gone");
        assertTrue(obfuscated.text().contains(PiiToken.OPEN), "a token must be present");
        assertEquals(input, obfuscator.deobfuscate(obfuscated.text()), "must restore exactly");
    }

    @Test
    void replacesAGenericSecretAssignment() {
        Rule rule = RuleFixtures.regex(
            "generic_secret", "SECRET",
            "(?i)(?:password|passwd|secret|api[_-]?key|token)\\s*[=:]\\s*[^\\s\"'.()\\[\\]{}\\\\]{6,}"
        );
        Obfuscator obfuscator = RuleFixtures.obfuscator(rule);
        String input = body("password=hunter2longvalue");
        ObfuscationResult obfuscated = obfuscator.obfuscate(input);
        assertFalse(obfuscated.text().contains("hunter2longvalue"), "secret value must be gone");
        assertTrue(obfuscated.text().contains(PiiToken.OPEN), "a token must be present");
        assertEquals(input, obfuscator.deobfuscate(obfuscated.text()), "must restore exactly");
    }
}
