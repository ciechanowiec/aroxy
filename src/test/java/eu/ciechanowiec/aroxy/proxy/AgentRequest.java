package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.client.ClientProfile;
import eu.ciechanowiec.aroxy.client.TransportFixtures;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import eu.ciechanowiec.aroxy.obfuscation.Respelling;
import eu.ciechanowiec.aroxy.obfuscation.RuleCompiler;
import eu.ciechanowiec.aroxy.obfuscation.RuleKind;
import eu.ciechanowiec.aroxy.obfuscation.RuleSpec;
import eu.ciechanowiec.aroxy.obfuscation.Vault;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * Builds a {@link RequestObfuscation} that applies a registered agent's own request body policy, so a
 * test reads what the profile declares rather than restating it.
 *
 * <p>The rule is one short loose literal, which is what makes a chance match inside encoded bytes
 * reproducible: a value of this length occurs in any long base64 run.
 */
@UtilityClass
final class AgentRequest {

    private static final String VALUE = "acme";

    /**
     * A request transformer carrying one loose literal rule and a profile's declared policy.
     *
     * @param profile the agent profile whose body policy applies
     * @return the transformer a test drives
     */
    static RequestObfuscation of(ClientProfile profile) {
        RuleSpec spec = new RuleSpec(
            "company", RuleKind.LITERAL, "COMPANY", VALUE, "i", false, new Respelling(true, false)
        );
        Obfuscator obfuscator = new Obfuscator(List.of(RuleCompiler.compile(spec)), new Vault(Optional.empty()));
        return new RequestObfuscation(TransportFixtures.buffered(profile).requestBody(), obfuscator);
    }
}
