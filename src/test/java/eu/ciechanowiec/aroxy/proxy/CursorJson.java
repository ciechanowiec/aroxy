package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.client.CursorCli;
import eu.ciechanowiec.aroxy.client.CursorJsonShape;
import eu.ciechanowiec.aroxy.client.CursorTransport;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import java.nio.charset.StandardCharsets;
import lombok.experimental.UtilityClass;

/**
 * Fixtures for Cursor's Connect-JSON dialect: the reader under test, the shape it reads with, and the
 * envelope shapes its tests build. Shared so the unit tests and the socket tests describe an envelope
 * the same way, and so a change to the dialect has one place to land.
 */
@UtilityClass
final class CursorJson {

    static final String TEXT_DELTA = "textDelta";

    static CursorResponseStream reader() {
        Obfuscator obfuscator = ProxyHarness.obfuscator();
        obfuscator.obfuscate(ProxyHarness.SECRET);
        return new CursorResponseStream(shape(), obfuscator);
    }

    static CursorJsonShape shape() {
        return ((CursorTransport) CursorCli.profile().transport()).json();
    }

    static String delta(String event, String fragment) {
        return "{\"interactionUpdate\":{\"" + event + "\":{\"text\":\"" + fragment + "\"}}}";
    }

    static ConnectJsonFrame frame(String json) {
        return new ConnectJsonFrame(0, json.getBytes(StandardCharsets.UTF_8));
    }

    static String payload(ConnectJsonFrame frame) {
        return new String(frame.payload(), StandardCharsets.UTF_8);
    }

    static String text(CursorResponseStream.Batch batch) {
        return batch.frames().stream().map(CursorJson::payload).reduce("", String::concat);
    }
}
