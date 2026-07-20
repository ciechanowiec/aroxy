package eu.ciechanowiec.aroxy.proxy;

import io.airlift.compress.MalformedInputException;
import io.airlift.compress.zstd.ZstdInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Inflates a zstd-compressed request body. Codex CLI compresses its requests this way by default when
 * signed in with ChatGPT, and a compressed body carries no text for the rules to match, so it is
 * inflated before obfuscation rather than forwarded opaque.
 *
 * <p>The streaming reader is used rather than the sized one on purpose: a frame produced by a
 * streaming encoder usually omits the content size from its header, which is exactly the case the
 * sized entry point cannot handle.
 *
 * <p>A body that does not inflate yields an empty result rather than an exception. The caller keeps it
 * marked as encoded, causing an in-scope request to be rejected locally rather than forwarded
 * unobfuscated. Note that a body which is not a frame at all raises {@link MalformedInputException},
 * which is unchecked and would otherwise escape as a 502.
 */
@Slf4j
@UtilityClass
final class ZstdBody {

    static final String ENCODING = "zstd";

    /**
     * Inflates a zstd frame.
     *
     * @param compressed the compressed body
     * @return the inflated body, or empty when the bytes are not a frame this reader can inflate
     */
    static Optional<byte[]> inflated(byte[] compressed) {
        try (InputStream in = new ZstdInputStream(new ByteArrayInputStream(compressed))) {
            return Optional.of(in.readAllBytes());
        } catch (IOException | MalformedInputException exception) {
            log.warn(
                "A {}-encoded request body could not be inflated: {}",
                ENCODING, exception.getMessage()
            );
            return Optional.empty();
        }
    }
}
