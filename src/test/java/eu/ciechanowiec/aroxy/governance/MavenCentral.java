package eu.ciechanowiec.aroxy.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Reads the latest stable comparable version of a dependency from the Maven Central metadata: the
 * numeric major for conventional versions, or the leading year for versions beginning with
 * {@code 20**}. Pre-release versions are ignored: the six forms the versions report ignores (see its
 * ruleset in the pom), spelled here as the same anchored patterns so the two agree on what counts as
 * stable, plus snapshots, which the report never meets because it reads release metadata. Any
 * unreachable registry, non-200 response, or absence of a stable release throws, so the freshness
 * check fails closed rather than passing on missing data.
 */
@UtilityClass
final class MavenCentral {

    private static final String BASE = "https://repo1.maven.org/maven2/";
    private static final Pattern PRERELEASE = Pattern.compile(
        "(?i).*alpha.*|.*beta.*|.*preview.*|.*snapshot.*|[0-9].+-m[0-9]+|[0-9].+\\.cr[0-9]+|[0-9].+-rc-?[0-9]*"
    );
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int OK = 200;

    static int latestMajor(DeclaredDependency dependency) {
        String metadata = fetch(metadataUrl(dependency));
        List<String> versions = MavenMetadata.versions(metadata);
        return versions.stream()
            .filter(version -> !PRERELEASE.matcher(version).matches())
            .filter(version -> DependencyFreshnessRules.sameScheme(dependency.version(), version))
            .map(DependencyFreshnessRules::major)
            .flatMapToInt(OptionalInt::stream)
            .max()
            .orElseThrow(() -> new IllegalStateException("No stable release found for " + dependency));
    }

    private static String metadataUrl(DeclaredDependency dependency) {
        String group = dependency.groupId().replace('.', '/');
        return BASE + group + "/" + dependency.artifactId() + "/maven-metadata.xml";
    }

    private static String fetch(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
            return body(url, send(client, request));
        }
    }

    private static String body(String url, HttpResponse<String> response) {
        if (response.statusCode() != OK) {
            throw new IllegalStateException("Registry returned " + response.statusCode() + " for " + url);
        }
        return response.body();
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new UncheckedIOException("Registry unreachable: " + request.uri(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while querying the registry", exception);
        }
    }
}
