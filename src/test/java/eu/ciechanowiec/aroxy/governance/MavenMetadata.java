package eu.ciechanowiec.aroxy.governance;

import java.util.List;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Reads the list of published versions out of a Maven {@code maven-metadata.xml} document. The order
 * is preserved as published, and the freshness check filters and compares the entries.
 */
@UtilityClass
final class MavenMetadata {

    private static final String VERSION = "version";

    static List<String> versions(String xml) {
        Document document = Xml.parse(xml);
        NodeList nodes = document.getElementsByTagName(VERSION);
        return IntStream.range(0, nodes.getLength())
            .mapToObj(nodes::item)
            .map(Node::getTextContent)
            .map(String::strip)
            .filter(text -> !text.isEmpty())
            .toList();
    }
}
