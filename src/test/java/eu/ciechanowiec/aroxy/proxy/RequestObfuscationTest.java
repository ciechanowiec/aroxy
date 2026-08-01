package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import eu.ciechanowiec.aroxy.client.ClaudeCode;
import eu.ciechanowiec.aroxy.client.CodexCli;
import eu.ciechanowiec.aroxy.client.RequestBodyPolicy;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import eu.ciechanowiec.aroxy.obfuscation.Vault;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Covers the body-policy branches at the boundary between agent protocols and obfuscation.
 */
class RequestObfuscationTest {

    // Both are the base64 alphabet and both contain the rule's value by chance, in a mixed case no
    // author would type. That mixed case is what a match inside encoded bytes looks like in the audit.
    private static final String IMAGE_BYTES = "iVBORw0KGgoaCMeAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ";
    private static final String SIGNATURE = "ErUBCkYIBxgCKaCMeAonlxDM8rlfcVeLc8hJP6WgHeaD75g";
    private static final String BODY = """
        {"messages":[{"role":"user","content":[
          {"type":"text","text":"acme ships it"},
          {"type":"image","source":{"type":"base64","media_type":"image/png","data":"%s"}},
          {"type":"thinking","thinking":"acme again","signature":"%s"}
        ]}]}""".formatted(IMAGE_BYTES, SIGNATURE);

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

    // A rule looks for a value in prose, yet a base64 attachment is a stretch of the same alphabet
    // long enough that a short value occurs in it by chance. Substituting there leaves bytes the
    // vendor cannot decode, and the whole attachment is rejected rather than the request failing
    // somewhere a reader could see the cause. The same holds for the signature that authenticates a
    // thinking block, which the vendor validates before it reads anything else.
    @Test
    void encodedAttachmentsAndSignaturesSurviveARuleThatMatchesTheirBytes() {
        JSONObject sent = new JSONObject(AgentRequest.of(ClaudeCode.profile()).apply(BODY).text());
        JSONArray content = sent.getJSONArray("messages").getJSONObject(0).getJSONArray("content");
        assertEquals(
            IMAGE_BYTES, content.getJSONObject(1).getJSONObject("source").getString("data"),
            "the attachment bytes leave exactly as they arrived"
        );
        assertEquals(
            SIGNATURE, content.getJSONObject(2).getString("signature"),
            "the thinking signature leaves exactly as it arrived"
        );
        assertEquals(
            "<<PII:COMPANY:1>> ships it", content.getJSONObject(0).getString("text"),
            "prose beside the attachment is still obfuscated"
        );
        assertEquals(
            "<<PII:COMPANY:1>> again", content.getJSONObject(2).getString("thinking"),
            "the thinking text the signature covers is obfuscated as before"
        );
    }

    // The names above are opaque only under their own parent, so a tool that declares a parameter
    // named "data" has its description scanned like any other.
    @Test
    void aToolParameterSharingAnOpaqueNameIsStillScanned() {
        String schema = "{\"properties\":{\"data\":{\"type\":\"string\",\"description\":\"acme rows\"}}}";
        JSONObject sent = new JSONObject(AgentRequest.of(ClaudeCode.profile()).apply(schema).text());
        assertEquals(
            "<<PII:COMPANY:1>> rows",
            sent.getJSONObject("properties").getJSONObject("data").getString("description"),
            "a tool parameter is prose the rules must still reach"
        );
    }

    // Codex attaches an image as a base64 data URL, which the API rejects as an invalid base64-encoded
    // value once a token has been written into it. The URL form of the same field is equally unusable
    // once rewritten, since the vendor has to dereference what it is given.
    @Test
    void anAttachedCodexImageSurvivesARuleThatMatchesItsDataUrl() {
        String body = """
            {"input":[{"role":"user","content":[
              {"type":"input_text","text":"acme ships it"},
              {"type":"input_image","image_url":"data:image/png;base64,%s"}
            ]}]}""".formatted(IMAGE_BYTES);
        JSONObject sent = new JSONObject(AgentRequest.of(CodexCli.profile()).apply(body).text());
        JSONArray content = sent.getJSONArray("input").getJSONObject(0).getJSONArray("content");
        assertEquals(
            "data:image/png;base64," + IMAGE_BYTES, content.getJSONObject(1).getString("image_url"),
            "the attached image leaves exactly as it arrived"
        );
        assertEquals(
            "<<PII:COMPANY:1>> ships it", content.getJSONObject(0).getString("text"),
            "prose beside the attachment is still obfuscated"
        );
    }

    private static RequestObfuscation request(RequestBodyPolicy policy) {
        return new RequestObfuscation(policy, new Obfuscator(List.of(), new Vault(Optional.empty())));
    }
}
