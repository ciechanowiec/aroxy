package eu.ciechanowiec.aroxy.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
        assertTrue(
            TransportFixtures.buffered(ClaudeCode.profile()).requestBody().json(),
            "Claude requests are structured JSON"
        );
        assertTrue(
            TransportFixtures.buffered(CodexCli.profile()).requestBody().json(),
            "Codex requests are structured JSON"
        );
    }

    @Test
    void onlyCodexDeclaresAnOpaqueProtocolField() {
        assertTrue(
            TransportFixtures.buffered(ClaudeCode.profile()).requestBody().opaqueFields().isEmpty(),
            "Claude has no opaque field"
        );
        assertEquals(
            Set.of("encrypted_content"), TransportFixtures.buffered(CodexCli.profile()).requestBody().opaqueFields(),
            "Codex ciphertext remains outside transformation"
        );
    }

    // Cursor carries its own transport rather than a buffered one. The distinction is made by the
    // sealed type, so a caller narrows it and the compiler checks the narrowing is exhaustive. There is
    // no accessor left that compiles for Cursor and fails at runtime.
    @Test
    void cursorCarriesItsOwnTransport() {
        assertInstanceOf(CursorTransport.class, CursorCli.profile().transport());
        assertInstanceOf(BufferedTransport.class, ClaudeCode.profile().transport());
        assertInstanceOf(BufferedTransport.class, CodexCli.profile().transport());
    }
}
