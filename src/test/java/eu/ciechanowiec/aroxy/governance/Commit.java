package eu.ciechanowiec.aroxy.governance;

/**
 * One commit reachable from the history: its abbreviated hash, its parsed {@link CommitMessage}, and
 * the {@link DiffStat} of the change it records.
 */
record Commit(String sha, CommitMessage message, DiffStat stat) {
}
