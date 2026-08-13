package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.aroxy.daemon.Home;
import eu.ciechanowiec.aroxy.daemon.RuntimeSnapshot;
import lombok.experimental.UtilityClass;

/**
 * Describes what the running daemon has in force, so a command that changed the configuration can
 * report the effect rather than only the bookkeeping. "Attached" answers what was recorded, which
 * leaves the question the user actually has: whether the proxy is redacting by these rules yet.
 */
@UtilityClass
final class ActiveRules {

    private static final String ACTIVE = "Active now";

    /**
     * The status of a configuration the running daemon has already taken up, naming how many rules
     * that leaves in effect when the daemon's published snapshot can be read.
     *
     * @param home the state directory holding the daemon's published snapshot
     * @return the status text
     */
    static String inForce(Home home) {
        return RuntimeSnapshot.read(home.runtimeFile())
            .map(snapshot -> ACTIVE + ", " + rules(snapshot.config().ruleIds().size()))
            .orElse(ACTIVE);
    }

    /**
     * How a rule count reads in a status line. It is exposed so a test can pin the wording without
     * standing up a daemon to publish a snapshot.
     *
     * @param count the number of rules in effect
     * @return the count phrased for a reader
     */
    static String rules(int count) {
        if (count == 1) {
            return "1 rule in effect";
        }
        return count + " rules in effect";
    }
}
