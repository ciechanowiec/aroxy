package eu.ciechanowiec.aroxy.proxy;

import java.io.Serial;

/**
 * A stand-in carrying the exact simple name {@code EofException} that some server containers raise
 * when the client vanishes mid-write, so the benign-disconnect classifier's by-type branch can be
 * exercised without depending on a container-specific class.
 */
// deliberately checked to mimic the container EofException whose by-type branch is under test
@SuppressWarnings(
    "CheckedExceptionClass"
)
final class EofException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    EofException() {
        super("end of file");
    }
}
