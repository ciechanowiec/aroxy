package eu.ciechanowiec.aroxy.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * Locates the repository working tree and reads the content a commit from it would carry. A path
 * deleted in an uncommitted change is absent from the working tree and therefore absent from a scan.
 * Binary files and files that are not valid UTF-8 are reported as absent text, so the callers scan only
 * decodable source and prose. The working tree is the current directory, which is the module base
 * directory under the build and the repository root under the git hooks.
 */
@UtilityClass
final class Repository {

    static Path root() {
        return Path.of("").toAbsolutePath().normalize();
    }

    /**
     * Every file a commit from this working tree would carry: what git already tracks, plus what is
     * present and not ignored.
     *
     * <p>A file only git tracks is the wrong set to scan. New work is untracked until it is staged, so
     * a scan of the tracked set alone would pass over a whole feature's worth of new sources and report
     * green on a tree it had not read, then fail on the next run once those files were committed. The
     * gate is meant to answer whether this tree is fit to commit, so it reads the tree that would be
     * committed. Ignored files stay out, being build output rather than content.
     *
     * @param root the working tree root
     * @return the committable files present in the working tree
     */
    static List<Path> trackedFiles(Path root) {
        String listing = GitPlumbing.run(
            root, List.of("ls-files", "-z", "--cached", "--others", "--exclude-standard")
        );
        return Arrays.stream(listing.split("\0"))
            .filter(name -> !name.isEmpty())
            .map(root::resolve)
            .filter(Files::isRegularFile)
            .distinct()
            .toList();
    }

    static Set<String> topLevelDirectories(Path root) {
        List<String> names = trackedFiles(root).stream()
            .map(root::relativize)
            .filter(relative -> relative.getNameCount() > 1)
            .map(relative -> relative.getName(0).toString())
            .toList();
        return Set.copyOf(names);
    }

    static Optional<String> readText(Path file) {
        byte[] bytes = readBytes(file);
        if (isBinary(bytes)) {
            return Optional.empty();
        }
        return decode(bytes);
    }

    private static byte[] readBytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        }
    }

    private static boolean isBinary(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                return true;
            }
        }
        return false;
    }

    private static Optional<String> decode(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return Optional.of(decoder.decode(ByteBuffer.wrap(bytes)).toString());
        } catch (CharacterCodingException _) {
            return Optional.empty();
        }
    }
}
