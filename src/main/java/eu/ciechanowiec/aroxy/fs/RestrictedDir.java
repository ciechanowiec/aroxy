package eu.ciechanowiec.aroxy.fs;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * Creates the state directories owner-only ({@code 0700}) so that no other local user can read the
 * vault, the audit and wire logs, or the file-based control channel inside {@code ~/.aroxy}. The
 * directory is the access-control boundary: a {@code 0700} parent keeps every file beneath it private
 * regardless of the file's own mode, which is why hardening is applied here, at each directory, rather
 * than at each writer (several of which - the redirected daemon logs, atomic-move temporary files - cannot
 * carry a creation mode anyway). The mode is set at creation, and re-applied to an already-existing
 * directory so an in-place upgrade is secured too. On a filesystem without POSIX permissions it is
 * skipped.
 */
@UtilityClass
public final class RestrictedDir {

    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rwx------");
    private static final boolean POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    /**
     * Creates {@code directory} and any missing parents owner-only, returning {@code directory}.
     *
     * @param directory the directory to create owner-only
     * @return the created directory
     * @throws IOException if the directory cannot be created
     */
    public static Path create(Path directory) throws IOException {
        Files.createDirectories(directory, attributes());
        harden(directory);
        return directory;
    }

    private static FileAttribute<?>[] attributes() {
        return POSIX
            ? new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(OWNER_ONLY)}
            : new FileAttribute<?>[0];
    }

    private static void harden(Path directory) {
        if (POSIX) {
            enforce(directory);
        }
    }

    private static void enforce(Path directory) {
        try {
            Files.setPosixFilePermissions(directory, OWNER_ONLY);
        } catch (IOException _) {
            // Best-effort: the create-time attribute already secured a freshly created directory.
        }
    }
}
