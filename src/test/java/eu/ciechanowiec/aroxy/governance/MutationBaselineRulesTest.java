package eu.ciechanowiec.aroxy.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The comparison has to separate two failures that a surviving-mutant count folds together: a mutant
 * nobody accepted, and an accepted mutant that is no longer alive.
 */
class MutationBaselineRulesTest {

    private static final String REPORT = """
        <mutations>
        <mutation detected='false' status='SURVIVED'><mutatedClass>a.B</mutatedClass>\
        <mutatedMethod>run</mutatedMethod><description>removed call to a/B::log</description></mutation>
        <mutation detected='true' status='KILLED'><mutatedClass>a.B</mutatedClass>\
        <mutatedMethod>run</mutatedMethod><description>negated conditional</description></mutation>
        <mutation detected='false' status='NO_COVERAGE'><mutatedClass>a.C</mutatedClass>\
        <mutatedMethod>stop</mutatedMethod><description>removed call to a/C::close</description></mutation>
        </mutations>
        """;

    @Test
    void readsOnlyTheMutantsThatAreStillAlive() {
        Set<MutationSurvivor> survivors = MutationBaselineRules.survivors(REPORT);
        assertEquals(2, survivors.size(), "a killed mutant is not a survivor");
        assertTrue(survivors.contains(MutationSurvivor.of("a.B", "run", "removed call to a/B::log")));
        assertTrue(survivors.contains(MutationSurvivor.of("a.C", "stop", "removed call to a/C::close")));
    }

    @Test
    void reportsASurvivorNobodyAccepted() {
        Set<MutationSurvivor> accepted = MutationBaselineRules.accepted(
            "a.B\trun\tremoved call to a/B::log\tthe log line is asserted elsewhere\n"
        );
        assertEquals(
            List.of("C.stop: removed call to a/C::close"),
            MutationBaselineRules.unaccepted(MutationBaselineRules.survivors(REPORT), accepted)
        );
    }

    @Test
    void reportsAnAcceptedMutantThatIsNoLongerAlive() {
        String baseline = "a.B\tgone\tremoved call to a/B::log\tstale entry\n";
        assertEquals(
            List.of("B.gone: removed call to a/B::log"),
            MutationBaselineRules.stale(
                MutationBaselineRules.survivors(REPORT),
                MutationBaselineRules.accepted(baseline),
                MutationBaselineRules.intermittent(baseline)
            )
        );
    }

    // A mutant whose outcome does not repeat must not be reported stale on the run that happens to kill
    // it, or the build is red whichever way the entry is recorded.
    @Test
    void keepsAnIntermittentEntryThatThisRunHappenedToKill() {
        String baseline = "a.B\tgone\tremoved call to a/B::log\t[intermittent] seen to flap\n";
        assertTrue(
            MutationBaselineRules.stale(
                MutationBaselineRules.survivors(REPORT),
                MutationBaselineRules.accepted(baseline),
                MutationBaselineRules.intermittent(baseline)
            ).isEmpty(),
            "a marked entry is not a line to delete"
        );
    }

    // The marker suspends only the stale half. A survivor nobody accepted is still a new gap, marked
    // neighbours or not, which is what keeps the marker from becoming a way to switch the gate off.
    @Test
    void stillReportsAnUnacceptedSurvivorBesideAnIntermittentEntry() {
        Set<MutationSurvivor> accepted = MutationBaselineRules.accepted(
            "a.B\trun\tremoved call to a/B::log\t[intermittent] seen to flap\n"
        );
        assertEquals(
            List.of("C.stop: removed call to a/C::close"),
            MutationBaselineRules.unaccepted(MutationBaselineRules.survivors(REPORT), accepted)
        );
    }

    @Test
    void marksOnlyTheEntriesThatSayTheyFlap() {
        String baseline = """
            a.B\trun\tremoved call to a/B::log\t[intermittent] flaps
            a.C\tstop\tremoved call to a/C::close\tan ordinary reason
            """;
        assertEquals(1, MutationBaselineRules.intermittent(baseline).size());
    }

    @Test
    void ignoresCommentsAndBlankLinesInTheBaseline() {
        Set<MutationSurvivor> accepted = MutationBaselineRules.accepted(
            "# a heading\n\na.B\trun\tremoved call to a/B::log\treason\n\n"
        );
        assertEquals(1, accepted.size(), "only real entries count");
    }

    @Test
    void rejectsABaselineLineThatCannotIdentifyAMutant() {
        assertThrows(
            IllegalStateException.class, () -> MutationBaselineRules.accepted("a.B\trun\n")
        );
    }
}
