package eu.ciechanowiec.aroxy.proxy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import lombok.experimental.UtilityClass;

/**
 * Shared HTTP test constants and a tiny POST helper, so the proxy tests stay free of repeated status
 * literals and request-building boilerplate.
 */
@UtilityClass
final class Http {

    static final int OK = 200;
    static final int BAD_REQUEST = 400;
    static final int UNSUPPORTED_MEDIA_TYPE = 415;
    static final int TOO_MANY_REQUESTS = 429;
    static final int BAD_GATEWAY = 502;
    static final String MESSAGES = "/v1/messages";
    static final String RESPONSES = "/responses";

    static HttpResponse<String> post(URI base, String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(base + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
