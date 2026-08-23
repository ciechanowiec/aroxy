package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Drives the Connect-JSON response reader directly, one envelope at a time. The socket tests prove the
 * path works end to end. This pins the decisions it makes on the way - which value is a fragment, when
 * a held partial is released, and what counts as the end of a turn - because each of those is a place
 * where a wrong answer still produces plausible-looking output.
 */
class CursorResponseStreamTest {

    private static final String TOKEN = "<<PII:AWS_KEY:1>>";
    private static final String TEXT = CursorJson.TEXT_DELTA;
    private static final String SECRET = ProxyHarness.SECRET;

    @Test
    void reversesAFragmentSplitAcrossFrames() {
        CursorResponseStream stream = CursorJson.reader();
        String first = CursorJson.text(stream.accept(CursorJson.frame(CursorJson.delta(TEXT, "here is <<PII:AWS_"))));
        String second = CursorJson.text(stream.accept(CursorJson.frame(CursorJson.delta(TEXT, "KEY:1>> done"))));
        assertTrue(first.contains("here is "), first);
        assertTrue(second.contains(SECRET), second);
        assertEquals(1, stream.reversedCount(), "the split token counts once, and only once");
    }

    @Test
    void reversesAWholeValueOutsideAnyFragmentField() {
        CursorResponseStream stream = CursorJson.reader();
        String out = CursorJson.text(
            stream.accept(CursorJson.frame("{\"toolResult\":{\"output\":\"" + TOKEN + "\"}}"))
        );
        assertTrue(out.contains(SECRET), out);
        assertEquals(1, stream.reversedCount(), "a whole value is counted too");
    }

    // A fragment run belongs to one field. When the stream switches to a different one, whatever is
    // still held belongs to the run that just ended and has to be released before the new run starts.
    @Test
    void releasesAHeldPartialWhenTheFragmentFieldChanges() {
        CursorResponseStream stream = CursorJson.reader();
        stream.accept(CursorJson.frame(CursorJson.delta(TEXT, "tail <<PII:AWS_")));
        String out = CursorJson.text(stream.accept(CursorJson.frame(CursorJson.delta("thinkingDelta", "elsewhere"))));
        assertTrue(out.contains("<<PII:AWS_"), "the partial from the previous run is emitted: " + out);
        assertTrue(out.contains("elsewhere"), out);
    }

    @Test
    void releasesAHeldPartialWhenTheTurnEnds() {
        CursorResponseStream stream = CursorJson.reader();
        stream.accept(CursorJson.frame(CursorJson.delta(TEXT, "trailing <<PII:AWS_")));
        String out = CursorJson.text(stream.accept(CursorJson.frame("{\"interactionUpdate\":{\"turnEnded\":{}}}")));
        assertTrue(out.contains("<<PII:AWS_"), "nothing held back is dropped at the end: " + out);
    }

    @Test
    void releasesAHeldPartialAtEndOfStream() {
        CursorResponseStream stream = CursorJson.reader();
        stream.accept(CursorJson.frame(CursorJson.delta(TEXT, "trailing <<PII:AWS_")));
        List<ConnectJsonFrame> flushed = stream.finish();
        assertEquals(1, flushed.size(), "the remainder is replayed as one more frame");
        assertTrue(
            CursorJson.payload(flushed.getFirst()).contains("<<PII:AWS_"), CursorJson.payload(flushed.getFirst())
        );
        assertTrue(stream.finish().isEmpty(), "a second flush has nothing left to release");
    }

    // The end of a turn is found by looking for the key in the parsed tree. A value that merely reads
    // like the marker is not the marker, and the completion keys differ from the turn-end key.
    @Test
    void recognisesCompletionKeysStructurally() {
        assertTrue(
            CursorJson.reader().accept(CursorJson.frame("{\"interactionUpdate\":{\"turnEnded\":{}}}")).complete(),
            "turnEnded ends the turn"
        );
        assertFalse(
            CursorJson.reader().accept(CursorJson.frame("{\"interactionUpdate\":{\"toolCallCompleted\":{}}}"))
                .complete(),
            "a tool call completing ends a fragment run but not the turn"
        );
        assertFalse(
            CursorJson.reader().accept(CursorJson.frame("{\"" + TEXT + "\":{\"text\":\"turnEnded\"}}")).complete(),
            "a value that merely reads like the marker does not end the turn"
        );
    }

    @Test
    void findsAFragmentNestedInsideAnArray() {
        CursorResponseStream stream = CursorJson.reader();
        String out = CursorJson.text(
            stream.accept(CursorJson.frame("{\"events\":[{\"" + TEXT + "\":{\"text\":\"" + TOKEN + "\"}}]}"))
        );
        assertTrue(out.contains(SECRET), "a fragment inside an array is still found: " + out);
    }

    @Test
    void treatsANonStringFragmentValueAsNoFragment() {
        CursorResponseStream stream = CursorJson.reader();
        String out = CursorJson.text(stream.accept(CursorJson.frame("{\"" + TEXT + "\":{\"text\":7}}")));
        assertTrue(out.contains("7"), out);
        assertEquals(0, stream.reversedCount(), "nothing was a fragment, and nothing was reversed");
    }

    @Test
    void rejectsCompressedAndNonObjectEnvelopes() {
        assertThrows(
            ConnectProtocolException.class,
            () -> CursorJson.reader().accept(new ConnectJsonFrame(1, "{}".getBytes(StandardCharsets.UTF_8)))
        );
        assertThrows(
            ConnectProtocolException.class,
            () -> CursorJson.reader().accept(CursorJson.frame("[1,2]"))
        );
    }

    @Test
    void reportsTheEndOfStreamFlagAsACompleteTurn() {
        CursorResponseStream stream = CursorJson.reader();
        CursorResponseBatch batch = stream.accept(
            new ConnectJsonFrame(ConnectJsonFrame.END_STREAM, "{}".getBytes(StandardCharsets.UTF_8))
        );
        assertTrue(batch.complete(), "the end-of-stream flag ends the turn on its own");
    }

    // A fragment frame can itself be the last one. The held partial has to be released after that
    // frame is emitted, not before it, or the tail would arrive ahead of the text it belongs to.
    @Test
    void releasesAHeldPartialAfterAFragmentFrameThatEndsTheStream() {
        CursorResponseStream stream = CursorJson.reader();
        stream.accept(CursorJson.frame(CursorJson.delta(TEXT, "start ")));
        CursorResponseBatch last = stream.accept(
            new ConnectJsonFrame(
                ConnectJsonFrame.END_STREAM,
                CursorJson.delta(TEXT, "tail <<PII:AWS_").getBytes(StandardCharsets.UTF_8)
            )
        );
        String out = CursorJson.text(last);
        assertTrue(out.contains("tail "), out);
        assertTrue(out.contains("<<PII:AWS_"), "the remainder is released once the fragment is out: " + out);
        assertTrue(out.indexOf("tail ") < out.lastIndexOf("<<PII:AWS_"), "and it follows that fragment");
    }

    // The fragment can sit under any key, so the search cannot stop at the first one that misses.
    // A completion marker can sit inside an array just as a fragment can, so the search for it has to
    // descend into arrays too. Missing it there would leave the turn looking unfinished.
    @Test
    void recognisesACompletionKeyNestedInsideAnArray() {
        assertTrue(
            CursorJson.reader().accept(CursorJson.frame("{\"events\":[{\"turnEnded\":{}}]}")).complete(),
            "a marker inside an array still ends the turn"
        );
    }

    @Test
    void findsAFragmentUnderALaterKey() {
        CursorResponseStream stream = CursorJson.reader();
        String out = CursorJson.text(
            stream.accept(CursorJson.frame("{\"meta\":{\"id\":\"1\"},\"" + TEXT + "\":{\"text\":\"" + TOKEN + "\"}}"))
        );
        assertTrue(out.contains(SECRET), "a fragment behind an unrelated key is still found: " + out);
    }

    // Reading an array as a fragment matters only for a split token: a whole one is restored either
    // way, so a test using a complete token proves nothing about the array path.
    @Test
    void treatsAValueInsideAnArrayAsAFragment() {
        CursorResponseStream stream = CursorJson.reader();
        String first = CursorJson.text(
            stream.accept(CursorJson.frame("{\"events\":[{\"" + TEXT + "\":{\"text\":\"a <<PII:AWS_\"}}]}"))
        );
        assertFalse(
            first.contains("<<PII:AWS_"),
            "an unfinished token inside an array is held back, not emitted: " + first
        );
        String second = CursorJson.text(
            stream.accept(CursorJson.frame("{\"events\":[{\"" + TEXT + "\":{\"text\":\"KEY:1>> b\"}}]}"))
        );
        assertTrue(second.contains(SECRET), "and completes on the next frame: " + second);
    }

    // A fragment frame can carry ordinary values beside its fragment. Those are reversed whole, and
    // their tally is added to the streamed one rather than taken off it.
    @Test
    void countsWholeValuesBesideTheFragment() {
        CursorResponseStream stream = CursorJson.reader();
        stream.accept(
            CursorJson.frame(
                "{\"interactionUpdate\":{\"" + TEXT + "\":{\"text\":\"plain\"},\"note\":\"" + TOKEN + "\"}}"
            )
        );
        assertEquals(1, stream.reversedCount(), "the sibling value counts once, and counts upward");
    }
}
