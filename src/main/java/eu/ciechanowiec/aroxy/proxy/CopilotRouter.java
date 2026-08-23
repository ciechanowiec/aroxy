package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.client.ClientProfile;
import eu.ciechanowiec.aroxy.client.CopilotTransport;
import eu.ciechanowiec.aroxy.obfuscation.ObfuscationResult;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.json.JSONException;

/**
 * Validates and forwards decrypted Copilot HTTP requests. Only approved host families can leave this
 * private listener, which prevents a changed Host header from turning interception into a general
 * credential-bearing forwarder.
 */
@Slf4j
final class CopilotRouter {

    private static final int BAD_REQUEST = 400;
    private static final int BAD_GATEWAY = 502;
    private static final int MISDIRECTED = 421;
    private static final int UNSUPPORTED_MEDIA_TYPE = 415;
    private static final int UPGRADE_REQUIRED = 426;
    private static final int HTTPS_PORT = 443;
    private final ProxyParts parts;
    private final ClientProfile profile;
    private final CopilotTransport transport;
    private final CopilotResponder responder;

    CopilotRouter(ProxyParts parts, CopilotTransport transport) {
        this.parts = parts;
        this.profile = parts.profile();
        this.transport = transport;
        this.responder = new CopilotResponder(parts, transport);
    }

    void serve(Request inbound, Response response) {
        try {
            this.route(inbound, response);
        } catch (JSONException exception) {
            log.warn("Rejected a Copilot request that could not be parsed safely: {}", exception.getMessage());
            reject(response, BAD_REQUEST, "aroxy could not safely parse this JSON request body");
        } catch (IOException exception) {
            log.warn("Copilot proxying failed: {}", exception.toString());
            reject(response, BAD_GATEWAY, "aroxy could not reach the Copilot upstream");
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            reject(response, BAD_GATEWAY, "Copilot upstream request was interrupted");
        }
    }

    private void route(Request inbound, Response response) throws IOException, InterruptedException {
        String host = Request.getServerName(inbound).toLowerCase(Locale.ROOT);
        int port = Request.getServerPort(inbound);
        if (!this.transport.intercepts(host, port)) {
            reject(response, MISDIRECTED, "Copilot destination is outside the protected host boundary");
            return;
        }
        ProxyRequest request = ProxyRequest.from(inbound).decoded();
        if (cannotTransform(request)) {
            reject(response, UNSUPPORTED_MEDIA_TYPE, "aroxy could not safely decode this request body");
        } else if (WebSocketUpgrade.isRequested(request.headers())) {
            reject(response, UPGRADE_REQUIRED, "aroxy transforms Copilot HTTP inference requests only");
        } else {
            this.forward(response, authority(host, port), request);
        }
    }

    private boolean cannotTransform(ProxyRequest request) {
        return this.profile.scope().transforms(request.path()) && request.hasBody() && request.isEncoded();
    }

    private void forward(
        Response response, String authority, ProxyRequest request
    ) throws IOException, InterruptedException {
        String stamp = this.parts.audit().nextStamp();
        byte[] body = this.obfuscate(request, stamp);
        URI destination = URI.create("https://" + authority + request.pathAndQuery());
        UpstreamReply reply = this.parts.upstream().sendTo(
            destination, request.method(), request.headers(), body
        );
        this.responder.respond(response, reply, request, stamp);
    }

    static String authority(String host, int port) {
        return port == HTTPS_PORT ? host : host + ":" + port;
    }

    private byte[] obfuscate(ProxyRequest request, String stamp) {
        if (!this.profile.scope().transforms(request.path()) || !request.hasBody()) {
            return request.body();
        }
        ObfuscationResult result = new RequestObfuscation(
            this.transport.requestBody(), this.parts.obfuscator()
        ).apply(new String(request.body(), StandardCharsets.UTF_8));
        byte[] redacted = result.text().getBytes(StandardCharsets.UTF_8);
        this.parts.audit().request(stamp, request.path(), result.matches());
        this.parts.audit().wireRedactedRequest(stamp, redacted, WireBody.TEXT);
        return redacted;
    }

    private static void reject(Response response, int status, String message) {
        CursorHttp.reject(response, status, message);
    }
}
