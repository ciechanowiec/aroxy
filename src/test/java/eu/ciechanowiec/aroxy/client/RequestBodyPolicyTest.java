package eu.ciechanowiec.aroxy.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Both current agents send structured JSON request bodies, while each profile independently declares
 * the protocol fields whose values must remain opaque.
 */
class RequestBodyPolicyTest {

    @Test
    void currentAgentsTraverseDecodedJsonStrings() {
        assertTrue(ClaudeCode.profile().requestBody().json(), "Claude requests are structured JSON");
        assertTrue(CodexCli.profile().requestBody().json(), "Codex requests are structured JSON");
    }

    @Test
    void onlyCodexDeclaresAnOpaqueProtocolField() {
        assertTrue(ClaudeCode.profile().requestBody().opaqueFields().isEmpty(), "Claude has no opaque field");
        assertEquals(
            Set.of("encrypted_content"), CodexCli.profile().requestBody().opaqueFields(),
            "Codex ciphertext remains outside transformation"
        );
    }
}
