package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.client.CursorCli;
import eu.ciechanowiec.aroxy.client.CursorTransport;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Connect-envelope and minimal protobuf fixtures shared by Cursor socket tests.
 */
@UtilityClass
final class CursorWire {

    static CursorConnectResponse responseTransformer() {
        Obfuscator obfuscator = ProxyHarness.obfuscator();
        obfuscator.obfuscate(ProxyHarness.SECRET);
        return new CursorConnectResponse(
            transport(), obfuscator, new ProtobufTransformer(new ProtobufText(obfuscator))
        );
    }

    static CursorTransport transport() {
        return (CursorTransport) CursorCli.profile().transport();
    }

    // The response frame nests the streamed fragment under interactionUpdate -> textDelta -> text,
    // which is the path CursorTransport names as chunked.
    static byte[] textDelta(String fragment) {
        return protobuf(protobuf(protobuf(fragment)));
    }

    static String requestJson() {
        return """
            {"runRequest":{"action":{"userMessageAction":{"userMessage":{"text":"%s"}}}}}
            """.formatted(ProxyHarness.SECRET).trim();
    }

    @SneakyThrows
    static byte[] frame(byte[] payload) {
        return framed(0, payload);
    }

    @SneakyThrows
    static byte[] framed(int flags, String payload) {
        return framed(flags, payload.getBytes(StandardCharsets.UTF_8));
    }

    @SneakyThrows
    static byte[] framed(int flags, byte[] payload) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new ConnectJsonFrame(flags, payload).write(output);
        return output.toByteArray();
    }

    static byte[] protobuf(String text) {
        return protobuf(text.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] protobuf(byte[] content) {
        return ByteBuffer.allocate(content.length + 2)
            .put((byte) 10)
            .put((byte) content.length)
            .put(content)
            .array();
    }

    @SneakyThrows
    static String payload(byte[] frame) {
        return ConnectJsonFrame.read(new ByteArrayInputStream(frame))
            .map(ConnectJsonFrame::payload)
            .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
            .orElseThrow();
    }

    static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static byte[] concat(byte[] first, byte[] second) {
        return ByteBuffer.allocate(first.length + second.length).put(first).put(second).array();
    }

    static int count(String text, CharSequence searched) {
        return (text.length() - text.replace(searched, "").length()) / searched.length();
    }
}
