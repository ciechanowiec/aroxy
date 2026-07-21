package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import eu.ciechanowiec.aroxy.client.RequestBodyPolicy;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import eu.ciechanowiec.aroxy.obfuscation.Vault;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.json.JSONException;
import org.junit.jupiter.api.Test;

/**
 * Covers the body-policy branches at the boundary between agent protocols and obfuscation.
 */
class RequestObfuscationTest {

    @Test
    void plainTextPolicyLeavesSerializationToTheObfuscator() {
        RequestObfuscation request = request(RequestBodyPolicy.text());
        assertEquals("not JSON", request.apply("not JSON").text(), "plain text needs no JSON parsing");
    }

    @Test
    void structuredPolicyPreservesANonStringRootValue() {
        RequestObfuscation request = request(RequestBodyPolicy.jsonExcept(Set.of()));
        assertEquals("42", request.apply("42").text(), "a JSON number is preserved");
    }

    @Test
    void structuredPolicyRejectsContentAfterTheRootValue() {
        RequestObfuscation request = request(RequestBodyPolicy.jsonExcept(Set.of()));
        assertThrows(
            JSONException.class,
            () -> request.apply("{}{}"),
            "a request body must contain exactly one JSON value"
        );
    }

    private static RequestObfuscation request(RequestBodyPolicy policy) {
        return new RequestObfuscation(policy, new Obfuscator(List.of(), new Vault(Optional.empty())));
    }
}
