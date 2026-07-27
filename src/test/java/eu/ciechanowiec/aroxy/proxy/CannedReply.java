package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.governance.Justification;
import java.util.Map;

/**
 * A canned upstream reply: a status code, a set of response headers, and a body string. SSE replies
 * set {@code Content-Type: text/event-stream}, and JSON replies set {@code application/json}.
 *
 * @param status  the HTTP status code to return
 * @param headers the response headers to send
 * @param body    the response body
 */
@Justification(
    "the headers map is an immutable Map.of() built by the factory methods"
)
@SuppressWarnings(
    "AssignmentOrReturnOfFieldWithMutableType"
)
record CannedReply(int status, Map<String, String> headers, String body) {

    static CannedReply json(String body) {
        return new CannedReply(Http.OK, Map.of("Content-Type", "application/json"), body);
    }

    static CannedReply sse(String body) {
        return new CannedReply(Http.OK, Map.of("Content-Type", "text/event-stream"), body);
    }

    static CannedReply connect(String body) {
        return new CannedReply(Http.OK, Map.of("Content-Type", "application/connect+json"), body);
    }

    static CannedReply connectProtobuf(String body) {
        return new CannedReply(Http.OK, Map.of("Content-Type", "application/connect+proto"), body);
    }

    static CannedReply protobuf() {
        return new CannedReply(Http.OK, Map.of("Content-Type", "application/proto"), "");
    }
}
