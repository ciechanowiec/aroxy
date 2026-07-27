package eu.ciechanowiec.aroxy.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.json.JSONObject;

/**
 * Jetty and Connect-protocol primitives shared by Cursor's routed exchanges.
 */
@UtilityClass
final class CursorHttp {

    private static final int MAX_MESSAGE = 64 * 1024 * 1024;
    private static final String CONNECT_JSON = "application/connect+json";
    private static final String CONNECT_PROTO = "application/connect+proto";

    static WireFormat requireConnectContentType(Request request) {
        Optional<String> contentType = Optional.ofNullable(request.getHeaders().get("Content-Type"));
        if (contentType.filter(value -> value.contains(CONNECT_JSON)).isPresent()) {
            return WireFormat.JSON;
        }
        if (contentType.filter(value -> value.contains(CONNECT_PROTO)).isPresent()) {
            return WireFormat.PROTOBUF;
        }
        throw ConnectProtocolException.unsupportedMedia(
            "Cursor agent stream must use application/connect+json or application/connect+proto"
        );
    }

    static void requireContentType(Request request, CharSequence required, String error) {
        Optional<String> contentType = Optional.ofNullable(request.getHeaders().get("Content-Type"));
        if (contentType.filter(value -> value.contains(required)).isEmpty()) {
            throw ConnectProtocolException.unsupportedMedia(error);
        }
    }

    static void ensureUncompressed(ConnectJsonFrame frame) {
        if (frame.compressed()) {
            throw ConnectProtocolException.unsupportedMedia(
                "Compressed Connect envelopes are unsupported"
            );
        }
    }

    static byte[] bounded(InputStream input) throws IOException {
        try (input) {
            byte[] body = input.readNBytes(MAX_MESSAGE + 1);
            if (body.length > MAX_MESSAGE) {
                throw new ConnectProtocolException("Cursor protobuf payload exceeds 64 MiB");
            }
            return body;
        }
    }

    static Map<String, List<String>> headers(Request request) {
        return HttpFields.asMap(request.getHeaders());
    }

    static String pathAndQuery(Request request) {
        return request.getHttpURI().getPathQuery();
    }

    static void prepare(
        Response response, int status, Map<String, List<String>> headers, int length
    ) {
        response.setStatus(status);
        ResponseHeaderCopier.copy(headers, response.getHeaders());
        if (length >= 0) {
            response.getHeaders().put("Content-Length", length);
        }
    }

    static void write(Content.Sink response, byte[] body) throws IOException {
        try (OutputStream output = Content.Sink.asOutputStream(response)) {
            output.write(body);
        }
    }

    static void write(Iterable<ConnectJsonFrame> frames, OutputStream output) throws IOException {
        for (ConnectJsonFrame frame : frames) {
            frame.write(output);
        }
    }

    static String message(ConnectProtocolException exception) {
        return Objects.toString(exception.getMessage(), "Invalid Cursor Connect stream");
    }

    static void reject(Response response, int status, String message) {
        if (response.isCommitted()) {
            return;
        }
        byte[] body = new JSONObject().put("type", "error").put("message", message)
            .toString().getBytes(StandardCharsets.UTF_8);
        response.setStatus(status);
        response.getHeaders().put("Content-Type", "application/json");
        response.getHeaders().put("Content-Length", body.length);
        try {
            write(response, body);
        } catch (IOException _) {
            // The client may already be gone.
        }
    }

    enum WireFormat {

        JSON,
        PROTOBUF
    }
}
