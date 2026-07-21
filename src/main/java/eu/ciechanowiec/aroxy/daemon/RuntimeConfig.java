package eu.ciechanowiec.aroxy.daemon;

import java.util.List;

/**
 * The safe-to-display portion of the configuration currently applied by the daemon. Rule values are
 * deliberately excluded: the CLI needs the ordered ids to describe the live configuration, but must
 * never copy sensitive patterns or replacement data into the runtime snapshot.
 *
 * @param maskSecrets whether sensitive matched values are masked in the audit log
 * @param ruleIds     the enabled rule ids, in application order
 */
public record RuntimeConfig(boolean maskSecrets, List<String> ruleIds) {

    public RuntimeConfig {
        ruleIds = List.copyOf(ruleIds);
    }

    @Override
    public List<String> ruleIds() {
        return List.copyOf(this.ruleIds);
    }
}
