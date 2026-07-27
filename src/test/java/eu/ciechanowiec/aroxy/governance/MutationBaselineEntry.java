package eu.ciechanowiec.aroxy.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * The mutation-baseline entry, run under the full profile after the mutation analysis it reads. Like
 * the freshness check it is an entry rather than a test, and for the same reason of ordering: the
 * report it compares against is produced at {@code package}, after the test phase has finished, so a
 * test could only ever read a stale report or none at all.
 *
 * <p>It replaces a surviving-mutant count. A count is satisfied by any set of the same size, so it
 * cannot notice one mutant being killed while another appears. Listing them separates the two failures
 * that matters: a survivor nobody accepted is a new gap in the tests, and an accepted entry that no
 * longer survives is a line to delete, which is what stops the list from drifting into a blanket
 * exemption.
 */
@UtilityClass
final class MutationBaselineEntry {

    private static final Path REPORT = Path.of("target/pit-reports/mutations.xml");
    private static final Path BASELINE = Path.of("mutation-baseline.tsv");

    static void main() {
        Path root = Repository.root();
        Set<MutationSurvivor> survivors = MutationBaselineRules.survivors(read(root, REPORT));
        Set<MutationSurvivor> accepted = MutationBaselineRules.accepted(read(root, BASELINE));
        failIfAny(
            MutationBaselineRules.unaccepted(survivors, accepted),
            "Mutants survived that " + BASELINE + " does not accept. Kill them, or record them with a reason:"
        );
        failIfAny(
            MutationBaselineRules.stale(
                survivors, accepted, MutationBaselineRules.intermittent(read(root, BASELINE))
            ),
            BASELINE + " accepts mutants that are now killed. Delete these lines:"
        );
    }

    private static String read(Path root, Path relative) {
        return Repository.readText(root.resolve(relative)).orElseThrow(
            () -> new IllegalStateException(
                "Missing " + relative + "; the mutation analysis must run before this check"
            )
        );
    }

    private static void failIfAny(Collection<String> offences, String headline) {
        if (!offences.isEmpty()) {
            throw new IllegalStateException(headline + "\n    " + String.join("\n    ", offences));
        }
    }
}
