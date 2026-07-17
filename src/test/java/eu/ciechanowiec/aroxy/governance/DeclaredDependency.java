package eu.ciechanowiec.aroxy.governance;

/**
 * A dependency the pom names directly, with its version already resolved from any property reference.
 * The freshness check compares this declared version against the latest release on Maven Central.
 */
record DeclaredDependency(String groupId, String artifactId, String version) {
}
