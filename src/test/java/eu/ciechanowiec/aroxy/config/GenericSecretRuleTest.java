package eu.ciechanowiec.aroxy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.obfuscation.ObfuscationResult;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import eu.ciechanowiec.aroxy.obfuscation.Rule;
import eu.ciechanowiec.aroxy.obfuscation.RuleCompiler;
import eu.ciechanowiec.aroxy.obfuscation.Vault;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Guards the bundled {@code generic_secret} rule against false positives on source code. Claude Code
 * sends file content as a JSON string with escaped newlines ({@code \n}) and {@code cat -n} line
 * numbers, so an unrestricted value class used to run a match past a line boundary into the next
 * line's number and to swallow ordinary code such as {@code token = obj.method()} or {@code x == token:}.
 * These fragments are worded exactly as they appear on the wire (escapes are literal backslash pairs).
 */
class GenericSecretRuleTest {

    private static final String TOKEN = "<<PII:SECRET:";

    private static Obfuscator bundledGenericSecret(Path directory) {
        Path target = directory.resolve("default.conf");
        new DefaultConfig(target).materialize();
        Rule rule = ConfigFile.read(target).rules().stream()
            .filter(entry -> "generic_secret".equals(entry.id()))
            .map(entry -> RuleCompiler.compile(entry.spec()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("bundled generic_secret rule missing"));
        return new Obfuscator(List.of(rule), new Vault(Optional.empty()));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "other_stack[-1] == token:\\n124\\t                other_stack.pop()",
            "token = raw[num].rstrip()\\n279\\t                if OTHER_DELIM_RE.match(token):",
            "token = line.strip()\\n103\\t                if verbatim is not None:",
            "token = None\\n104\\t        if verbatim is not None:",
            "token = self.current.value\\n55\\t        return token"
        }
    )
    void leavesSourceCodeUntouched(String wireFragment, @TempDir Path directory) {
        ObfuscationResult result = bundledGenericSecret(directory).obfuscate(wireFragment);
        assertFalse(result.text().contains(TOKEN), "code must not be mistaken for a secret: " + wireFragment);
        assertEquals(wireFragment, result.text(), "the code fragment must pass through unchanged");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "password=hunter2longvalue",
            "api_key: sk-ant-abc123def456ghij",
            "TOKEN=ghp_1234567890abcdefghij",
            "secret = s3cr3t_p+ss/w0rd=val"
        }
    )
    void stillMasksGenuineSecretAssignments(String wireFragment, @TempDir Path directory) {
        Obfuscator obfuscator = bundledGenericSecret(directory);
        ObfuscationResult result = obfuscator.obfuscate(wireFragment);
        assertTrue(result.text().contains(TOKEN), "a real secret must be masked: " + wireFragment);
        assertEquals(wireFragment, obfuscator.deobfuscate(result.text()), "must restore exactly");
    }
}
