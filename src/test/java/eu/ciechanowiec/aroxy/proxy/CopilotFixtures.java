package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.client.BufferedTransport;
import eu.ciechanowiec.aroxy.client.ClientId;
import eu.ciechanowiec.aroxy.client.ClientProfile;
import eu.ciechanowiec.aroxy.client.CodexCli;
import eu.ciechanowiec.aroxy.client.CopilotTransport;
import eu.ciechanowiec.aroxy.client.Gateway;
import eu.ciechanowiec.aroxy.client.PathScope;
import eu.ciechanowiec.aroxy.client.RequestBodyPolicy;
import java.net.URI;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * The localhost-only Copilot profile shared by socket-level tests.
 */
@UtilityClass
final class CopilotFixtures {

    static ClientProfile profile() {
        BufferedTransport responses = CodexCli.transport();
        CopilotTransport transport = new CopilotTransport(
            Set.of("localhost"), RequestBodyPolicy.jsonExcept(Set.of("encrypted_content")),
            responses, responses
        );
        return new ClientProfile(
            new ClientId("copilot-test"), "Copilot test",
            new Gateway(0, URI.create("https://api.githubcopilot.com")),
            new PathScope(List.of("/responses", "/chat/completions"), "Copilot inference"), transport
        );
    }
}
