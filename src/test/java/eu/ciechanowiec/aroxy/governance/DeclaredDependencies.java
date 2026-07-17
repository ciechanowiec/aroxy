package eu.ciechanowiec.aroxy.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Reads the directly declared dependencies out of a pom, resolving each {@code ${property}} version
 * against the pom's own properties. Only {@code /project/dependencies} is read, so the dependencies a
 * plugin declares for its own classpath are left out, matching the guideline's notion of a declared
 * dependency.
 */
@UtilityClass
final class DeclaredDependencies {

    private static final String PROPERTY_PREFIX = "${";
    private static final String PROPERTY_SUFFIX = "}";

    static List<DeclaredDependency> from(Path pom) {
        Node root = Xml.parse(read(pom)).getDocumentElement();
        Map<String, String> properties = properties(root);
        return Xml.firstChild(root, "dependencies")
            .map(dependencies -> Xml.children(dependencies, "dependency"))
            .orElseGet(List::of)
            .stream()
            .map(dependency -> toDependency(dependency, properties))
            .flatMap(Optional::stream)
            .toList();
    }

    private static Optional<DeclaredDependency> toDependency(Node element, Map<String, String> properties) {
        Optional<String> group = Xml.text(element, "groupId");
        Optional<String> artifact = Xml.text(element, "artifactId");
        Optional<String> version = Xml.text(element, "version").map(raw -> resolve(raw, properties));
        if (group.isPresent() && artifact.isPresent() && version.isPresent()) {
            return Optional.of(new DeclaredDependency(group.get(), artifact.get(), version.get()));
        }
        return Optional.empty();
    }

    private static String resolve(String raw, Map<String, String> properties) {
        if (raw.startsWith(PROPERTY_PREFIX) && raw.endsWith(PROPERTY_SUFFIX)) {
            String key = raw.substring(PROPERTY_PREFIX.length(), raw.length() - PROPERTY_SUFFIX.length());
            return properties.getOrDefault(key, raw);
        }
        return raw;
    }

    private static Map<String, String> properties(Node root) {
        return Xml.firstChild(root, "properties")
            .map(DeclaredDependencies::entries)
            .orElseGet(Map::of);
    }

    private static Map<String, String> entries(Node properties) {
        NodeList nodes = properties.getChildNodes();
        return IntStream.range(0, nodes.getLength())
            .mapToObj(nodes::item)
            .filter(node -> node.getNodeType() == Node.ELEMENT_NODE)
            .map(Element.class::cast)
            .collect(
                Collectors.toMap(
                    Element::getTagName, element -> element.getTextContent().strip(), (first, second) -> second
                )
            );
    }

    private static String read(Path pom) {
        try {
            return Files.readString(pom);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + pom, exception);
        }
    }
}
