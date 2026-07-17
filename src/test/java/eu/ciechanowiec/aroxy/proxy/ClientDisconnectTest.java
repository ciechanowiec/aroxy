package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A client that opens a request and then drops the socket mid-response must not error the proxy: it
 * has to keep serving. The disconnect is forced with a raw socket that is closed right after the
 * request line, and the proxy is then proven healthy by a normal follow-up request.
 */
class ClientDisconnectTest {

    @Test
    @SneakyThrows
    void clientDisconnectDoesNotErrorTheServer(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json("{\"answer\":\"ok\"}"));
        ProxyHarness harness = new ProxyHarness(upstream, logs);
        try {
            abruptlyDisconnect(harness.base());
            HttpResponse<String> after = followUp(harness.base());
            assertEquals(Http.OK, after.statusCode(), "the proxy keeps serving after a client disconnect");
            assertTrue(after.body().contains("ok"), "the next request is answered normally");
        } finally {
            harness.stop();
        }
    }

    @SneakyThrows
    private static void abruptlyDisconnect(URI base) {
        try (Socket socket = new Socket(base.getHost(), base.getPort())) {
            writePartialRequest(socket.getOutputStream());
            socket.shutdownOutput();
        }
    }

    @SneakyThrows
    private static void writePartialRequest(OutputStream out) {
        out.write(
            ("POST " + Http.MESSAGES + " HTTP/1.1\r\nHost: x\r\nContent-Length: 50\r\n\r\n{\"q\":\"")
                .getBytes(StandardCharsets.UTF_8)
        );
        out.flush();
    }

    @SneakyThrows
    private static HttpResponse<String> followUp(URI base) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(base + Http.MESSAGES))
            .POST(HttpRequest.BodyPublishers.ofString("{\"q\":\"hi\"}"))
            .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
