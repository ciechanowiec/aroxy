package eu.ciechanowiec.aroxy.proxy;

import java.util.List;

/**
 * Frames emitted from one response input and whether they complete the turn.
 *
 * @param frames   the emitted frames
 * @param complete whether the turn is complete
 */
record CursorResponseBatch(List<ConnectJsonFrame> frames, boolean complete) {

    CursorResponseBatch {
        frames = List.copyOf(frames);
    }
}
