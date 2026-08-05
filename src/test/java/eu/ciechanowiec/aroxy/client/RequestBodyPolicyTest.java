package eu.ciechanowiec.aroxy.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void eachAgentDeclaresTheProtocolFieldsItEncodes() {
        assertEquals(
            Set.of("source.data", "content.signature", "content.data"),
            TransportFixtures.buffered(ClaudeCode.profile()).requestBody().opaqueFields(),
            "Claude attachment bytes and thinking signatures remain outside transformation"
        );
        assertEquals(
            Set.of("encrypted_content", "content.image_url", "output.image_url"),
            TransportFixtures.buffered(CodexCli.profile()).requestBody().opaqueFields(),
            "Codex ciphertext and attached images remain outside transformation"
        );
    }

    // A bare name is opaque wherever it is reached, while a qualified one is opaque only under its
    // parent. The distinction is what lets an attachment stay untouched while a tool parameter that
    // happens to share the name "data" is still scanned.
    @Test
    void aQualifiedNameIsOpaqueOnlyUnderItsParent() {
        RequestBodyPolicy policy = RequestBodyPolicy.jsonExcept(Set.of("source.data", "encrypted_content"));
        assertTrue(policy.opaque("source", "data"), "attachment bytes are opaque");
        assertFalse(policy.opaque("properties", "data"), "a tool parameter of the same name is scanned");
        assertFalse(policy.opaque("", "data"), "the root carries no qualifier");
        assertTrue(policy.opaque("anywhere", "encrypted_content"), "a bare name needs no parent");
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
