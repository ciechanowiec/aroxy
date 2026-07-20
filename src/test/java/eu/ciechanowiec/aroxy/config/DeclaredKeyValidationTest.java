package eu.ciechanowiec.aroxy.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.client.ClaudeCode;
import eu.ciechanowiec.aroxy.client.ClientId;
import eu.ciechanowiec.aroxy.client.ClientProfile;
import eu.ciechanowiec.aroxy.client.ClientRegistry;
import java.net.InetAddress;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
        return EffectiveConfig.merge(List.of(file)).settings().gateway(ClaudeCode.ID).listenPort();
    }

    /**
     * Driven over every registered client rather than over one, because the listen keys are derived
     * from the client registry rather than listed in the parser. A test that only ever exercised one
     * agent's key would pass just as happily if a newly registered agent's key were never validated,
     * which is exactly the claim this is here to hold up.
     */
    @ParameterizedTest
    @MethodSource(
        "clientIds"
    )
    void rejectsAnOutOfRangePortNamingTheValueAndTheRange(ClientId id) {
        InvalidConfigException rejected = gateway(id.configKey() + " = 87788");
        assertTrue(rejected.getMessage().contains("87788"), rejected.getMessage());
        assertTrue(rejected.getMessage().contains("between 1 and 65535"), rejected.getMessage());
        assertTrue(rejected.getMessage().contains("broken"), rejected.getMessage());
    }

    @ParameterizedTest
    @MethodSource(
        "clientIds"
    )
    void rejectsPortZeroRatherThanBindingARandomPort(ClientId id) {
        assertTrue(
            gateway(id.configKey() + " = 127.0.0.1:0").getMessage().contains("'127.0.0.1:0'"),
            "port 0 is not configurable, and the value is echoed as written"
        );
    }

    @ParameterizedTest
    @MethodSource(
        "clientIds"
    )
    void rejectsANonNumericPort(ClientId id) {
        assertTrue(
            gateway(id.configKey() + " = eight-seven-eight-eight").getMessage().contains("is not a port"),
            "garbage is caught"
        );
    }

    @ParameterizedTest
    @MethodSource(
        "clientIds"
    )
    void rejectsAHostThatNamesNoPort(ClientId id) {
        String loopback = InetAddress.getLoopbackAddress().getHostAddress();
        assertTrue(
            gateway(id.configKey() + " = " + loopback).getMessage().contains(loopback),
            "a host alone declares no port"
        );
    }

    @ParameterizedTest
    @MethodSource(
        "clientIds"
    )
    void aValidPortIsHonoredForEveryClient(ClientId id) {
        ConfigFile file = assertDoesNotThrow(
            () -> ConfigFile.parse("valid", "[gateway]\n" + id.configKey() + " = 127.0.0.1:9999\n")
        );
        assertEquals(
            OVERRIDE_PORT, EffectiveConfig.merge(List.of(file)).settings().gateway(id).listenPort(),
            "every client's key reaches its own listener"
        );
    }

    private static Stream<ClientId> clientIds() {
        return ClientRegistry.profiles().stream().map(ClientProfile::id);
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
        ClientRegistry.profiles().stream().map(ClientProfile::id).forEach(
            id -> assertTrue(
                rejected.getMessage().contains(id.configKey()),
                "the rejection names every key the stanza does take, missing " + id.configKey()
            )
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
        ConfigFile file = assertDoesNotThrow(
            () -> ConfigFile.parse("valid", "[gateway]\nlisten_claude = 127.0.0.1:9999\n")
        );
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
