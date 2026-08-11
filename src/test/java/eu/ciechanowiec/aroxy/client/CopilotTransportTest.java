package eu.ciechanowiec.aroxy.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pins Copilot's dynamic host boundary and route-specific inference shapes.
 */
class CopilotTransportTest {

    private static final int HTTPS = 443;
    private static final int COPILOT_PORT = 8791;

    @Test
    void interceptsOnlyCopilotHostFamilies() {
        CopilotTransport transport = transport();
        assertTrue(transport.intercepts("api.business.githubcopilot.com", HTTPS));
        assertTrue(transport.intercepts("API.SUBDOMAIN.GHE.COM", HTTPS));
        assertFalse(transport.intercepts("github.com", HTTPS));
        assertFalse(transport.intercepts("githubcopilot.com.attacker.test", HTTPS));
        assertFalse(transport.intercepts("api.githubcopilot.com", 0));
    }

    @Test
    void selectsEachSupportedInferenceShape() {
        CopilotTransport transport = transport();
        assertTrue(transport.buffered("/responses").isPresent());
        assertTrue(transport.buffered("/v1/messages").isPresent());
        assertTrue(transport.chatCompletions("/chat/completions"));
        assertTrue(transport.chatCompletions("/v1/chat/completions"));
        assertFalse(transport.chatCompletions("/models"));
        assertTrue(transport.buffered("/models").isEmpty());
    }

    @Test
    void profileCarriesItsPortScopeAndOpaqueFields() {
        ClientProfile profile = CopilotCli.profile();
        CopilotTransport transport = (CopilotTransport) profile.transport();
        assertEquals(COPILOT_PORT, profile.gateway().listenPort());
        assertTrue(profile.scope().transforms("/v1/responses"));
        assertTrue(transport.requestBody().opaque("content", "image_url"));
        assertTrue(transport.requestBody().opaque("", "encrypted_content"));
    }

    private static CopilotTransport transport() {
        BufferedTransport buffered = new BufferedTransport(
            RequestBodyPolicy.text(), new SseDialect(List.of(), Set.of(), Set.of())
        );
        return new CopilotTransport(
            Set.of("githubcopilot.com", "ghe.com"), RequestBodyPolicy.text(), buffered, buffered
        );
    }
}
