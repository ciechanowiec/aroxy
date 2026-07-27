package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.client.BufferedTransport;
import eu.ciechanowiec.aroxy.client.ClaudeCode;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import eu.ciechanowiec.aroxy.obfuscation.StreamDeobfuscator;
import eu.ciechanowiec.governance.Justification;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * Drives the SSE transformer directly, chunk by chunk, rather than through a socket. The reassembly
 * test proves whole events survive. This pins what happens at the edges - a chunk that ends mid-event,
 * a stream that ends mid-token, and the flushing that decides whether the agent sees a delta now or
 * only once more bytes happen to arrive.
 */
@Justification(
    "the recording sink wraps an in-memory buffer, so there is nothing for a close to release"
)
@SuppressWarnings(
    "PMD.CloseResource"
)
class SseTransformerTest {

    private static final String SECRET = ProxyHarness.SECRET;
    private static final String TOKEN = "<<PII:AWS_KEY:1>>";

    @Test
    @SneakyThrows
    void reassemblesAnEventSplitAcrossTwoChunks() {
        Recording sink = new Recording();
        SseTransformer transformer = transformer(sink);
        byte[] event = delta().getBytes(StandardCharsets.UTF_8);
        int split = event.length / 2;
        transformer.push(Arrays.copyOfRange(event, 0, split), split);
        assertEquals("", sink.text(), "nothing is emitted while the event is still incomplete");
        byte[] rest = Arrays.copyOfRange(event, split, event.length);
        transformer.push(rest, rest.length);
        assertTrue(sink.text().contains(SECRET), sink.text());
    }

    // A carry left over from one chunk has to be preserved for the next one. Dropping it, or
    // mismeasuring its length, loses the head of the following event.
    @Test
    @SneakyThrows
    void carriesAPartialEventPastACompleteOne() {
        Recording sink = new Recording();
        SseTransformer transformer = transformer(sink);
        String batch = delta() + "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\"";
        push(transformer, batch);
        assertTrue(sink.text().contains(SECRET), sink.text());
        push(transformer, ":\"text_delta\",\"text\":\"tail\"}}\n\n");
        assertTrue(sink.text().contains("tail"), "the carried head is not lost: " + sink.text());
    }

    @Test
    @SneakyThrows
    void flushesEveryPushSoADeltaReachesTheAgentImmediately() {
        Recording sink = new Recording();
        SseTransformer transformer = transformer(sink);
        push(transformer, delta());
        assertTrue(sink.flushes() > 0, "a streamed delta is pushed out, not left in a buffer");
    }

    // Whatever is still buffered when the upstream stops has to be written out: a trailing event with
    // no delimiter, and a token held back waiting for a completion that will never arrive.
    @Test
    @SneakyThrows
    void emitsTheTrailingEventAndTheHeldPartialOnFinish() {
        Recording sink = new Recording();
        SseTransformer transformer = transformer(sink);
        push(
            transformer, "data: {\"type\":\"content_block_delta\",\"delta\":"
                + "{\"type\":\"text_delta\",\"text\":\"end <<PII:AWS_\"}}"
        );
        transformer.finish();
        assertTrue(sink.text().contains("end "), "the undelimited trailing event is written: " + sink.text());
        assertTrue(sink.text().contains("<<PII:AWS_"), "the held partial is written: " + sink.text());
        assertTrue(sink.flushes() > 0, "the final bytes are pushed out");
    }

    @Test
    @SneakyThrows
    void finishingTwiceEmitsNothingTheSecondTime() {
        Recording sink = new Recording();
        SseTransformer transformer = transformer(sink);
        push(
            transformer, "data: {\"type\":\"content_block_delta\",\"delta\":"
                + "{\"type\":\"text_delta\",\"text\":\"tail\"}}"
        );
        transformer.finish();
        String afterFirst = sink.text();
        transformer.finish();
        assertEquals(afterFirst, sink.text(), "the buffer is cleared, so nothing is written twice");
    }

    @Test
    @SneakyThrows
    void finishingAnEmptyStreamWritesNothingButStillFlushes() {
        Recording sink = new Recording();
        transformer(sink).finish();
        assertEquals("", sink.text(), "an empty buffer contributes no output");
        assertTrue(sink.flushes() > 0, "the end of a stream is pushed out even when it adds no bytes");
    }

    @SneakyThrows
    private static void push(SseTransformer transformer, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        transformer.push(bytes, bytes.length);
    }

    private static String delta() {
        return "data: {\"type\":\"content_block_delta\",\"delta\":"
            + "{\"type\":\"text_delta\",\"text\":\"" + TOKEN + "\"}}\n\n";
    }

    private static SseTransformer transformer(OutputStream sink) {
        Obfuscator obfuscator = ProxyHarness.obfuscator();
        obfuscator.obfuscate(SECRET);
        return new SseTransformer(
            sink, new StreamDeobfuscator(obfuscator),
            ((BufferedTransport) ClaudeCode.profile().transport()).dialect()
        );
    }

    /**
     * Records what was written and how often the stream was flushed, because "the agent sees this delta
     * now" is a property of the flush, not of the bytes.
     */
    private static final class Recording extends OutputStream {

        private final ByteArrayOutputStream written;
        private int flushes;

        Recording() {
            this.written = new ByteArrayOutputStream();
            this.flushes = 0;
        }

        @Override
        public void write(int value) {
            this.written.write(value);
        }

        @Override
        public void flush() {
            this.flushes++;
        }

        String text() {
            return this.written.toString(StandardCharsets.UTF_8);
        }

        int flushes() {
            return this.flushes;
        }
    }
}
