package eu.ciechanowiec.aroxy.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Locks the rule that a key a file declares must be usable. A key its stanza does not define, or a
 * value that names nothing the proxy can act on, breaks the whole file rather than being dropped for a
 * default: a configuration that reads as applied and is not would leave the user trusting a proxy that
 * never took the instruction. An absent key declares no intent and keeps the documented default.
 */
class DeclaredKeyValidationTest {

    private static final int DEFAULT_PORT = 8788;
    private static final int OVERRIDE_PORT = 9999;
    private static final String RULE = "[rule]\nid = r\nplaceholder = COMPANY\npattern = Acme\n";

    private static InvalidConfigException rejectionOf(String text) {
        return assertThrows(
            InvalidConfigException.class, () -> ConfigFile.parse("broken", text),
            "a declared key that names nothing usable breaks the file"
        );
    }

    private static InvalidConfigException gateway(String line) {
        return rejectionOf("[gateway]\n" + line + "\n");
    }

    private static int portOf(ConfigFile file) {
        return EffectiveConfig.merge(List.of(file)).settings().listenPort();
    }

    @Test
    void rejectsAnOutOfRangePortNamingTheValueAndTheRange() {
        InvalidConfigException rejected = gateway("listen = 87788");
        assertTrue(rejected.getMessage().contains("87788"), rejected.getMessage());
        assertTrue(rejected.getMessage().contains("between 1 and 65535"), rejected.getMessage());
        assertTrue(rejected.getMessage().contains("broken"), rejected.getMessage());
    }

    @Test
    void rejectsPortZeroRatherThanBindingARandomPort() {
        assertTrue(
            gateway("listen = 127.0.0.1:0").getMessage().contains("'127.0.0.1:0'"),
            "port 0 is not configurable, and the value is echoed as written"
        );
    }

    @Test
    void rejectsANonNumericPort() {
        assertTrue(
            gateway("listen = eight-seven-eight-eight").getMessage().contains("is not a port"),
            "garbage is caught"
        );
    }

    @Test
    void rejectsAHostThatNamesNoPort() {
        String loopback = InetAddress.getLoopbackAddress().getHostAddress();
        assertTrue(
            gateway("listen = " + loopback).getMessage().contains(loopback), "a host alone declares no port"
        );
    }

    @Test
    void rejectsAMaskSecretsValueThatIsNotABoolean() {
        InvalidConfigException rejected = gateway("mask_secrets = yes");
        assertTrue(rejected.getMessage().contains("mask_secrets 'yes'"), rejected.getMessage());
        assertTrue(rejected.getMessage().contains("true or false"), rejected.getMessage());
    }

    @Test
    void rejectsAnUpstreamKeyRatherThanIgnoringIt() {
        InvalidConfigException rejected = gateway("upstream = https://proxy.internal");
        assertTrue(rejected.getMessage().contains("'upstream'"), rejected.getMessage());
        assertTrue(
            rejected.getMessage().contains("listen"),
            "the rejection names the keys the stanza does take"
        );
    }

    @Test
    void rejectsAnUnknownRuleKey() {
        InvalidConfigException rejected = rejectionOf(RULE + "sensitivity = true\n");
        assertTrue(rejected.getMessage().contains("'sensitivity'"), rejected.getMessage());
        assertTrue(rejected.getMessage().contains("sensitive"), "the rejection names the key that was meant");
    }

    @Test
    void rejectsARuleTypeThatIsNeitherLiteralNorRegex() {
        InvalidConfigException rejected = rejectionOf(
            "[rule]\nid = r\nplaceholder = COMPANY\ntype = literl\nvalue = Acme\n"
        );
        assertTrue(rejected.getMessage().contains("type 'literl'"), rejected.getMessage());
        assertTrue(
            rejected.getMessage().contains("literal or regex"),
            "a mistyped type would otherwise read the stanza as a regex rule"
        );
    }

    @Test
    void rejectsAnEnabledValueThatIsNotABoolean() {
        assertTrue(
            rejectionOf(RULE + "enabled = maybe\n").getMessage().contains("enabled 'maybe'"),
            "a rule that neither enables nor disables is broken"
        );
    }

    @Test
    void rejectsASensitiveValueThatIsNotABoolean() {
        assertTrue(
            rejectionOf(RULE + "sensitive = 1\n").getMessage().contains("sensitive '1'"),
            "a half-read sensitive flag would decide whether a secret reaches the audit log"
        );
    }

    @Test
    void anAbsentListenKeyIsNoIntentAndKeepsTheDefault() {
        ConfigFile file = assertDoesNotThrow(() -> ConfigFile.parse("quiet", "[gateway]\nmask_secrets = false\n"));
        assertEquals(DEFAULT_PORT, portOf(file), "declaring no listen key keeps the default port");
    }

    @Test
    void aValidListenKeyStillOverridesTheDefault() {
        ConfigFile file = assertDoesNotThrow(() -> ConfigFile.parse("valid", "[gateway]\nlisten = 127.0.0.1:9999\n"));
        assertEquals(OVERRIDE_PORT, portOf(file), "a valid port is still honored");
    }

    @Test
    void aRuleDeclaringOnlyItsRequiredKeysTakesTheDocumentedDefaults() {
        ConfigFile file = assertDoesNotThrow(() -> ConfigFile.parse("defaults", RULE));
        assertEquals(
            1, EffectiveConfig.merge(List.of(file)).rules().size(), "an absent enabled key defaults to true"
        );
    }
}
