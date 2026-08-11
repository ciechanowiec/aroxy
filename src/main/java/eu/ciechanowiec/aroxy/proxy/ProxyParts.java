package eu.ciechanowiec.aroxy.proxy;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import eu.ciechanowiec.aroxy.client.ClientProfile;
import eu.ciechanowiec.aroxy.daemon.Home;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;

/**
 * The collaborators one client's listener needs to handle a request: the {@link Obfuscator} that
 * rewrites bodies in both directions, the {@link Upstream} that forwards to that client's API, the
 * {@link AuditLog} that records proof artifacts, and the {@link ClientProfile} that says which paths
 * are transformed and how that client's stream is shaped. Bundling them keeps the wiring constructors
 * within the parameter limit while staying explicit about what the proxy depends on.
 *
 * <p>The obfuscator and the audit log are shared across every listener, so one vault, one rule set,
 * and one audit trail serve every agent on the machine. The upstream and the profile are per client.
 *
 * @param obfuscator the engine used to obfuscate requests and deobfuscate responses
 * @param upstream   the forwarder to this client's API
 * @param audit      the audit log and wire-dump writer
 * @param profile    the client this listener serves
 * @param home       the state layout used by listener-owned private material
 */
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP",
    justification = "This is a dependency bundle: every exchange intentionally shares the live audit collaborator."
)
public record ProxyParts(
    Obfuscator obfuscator, Upstream upstream, AuditLog audit, ClientProfile profile, Home home
) {
}
