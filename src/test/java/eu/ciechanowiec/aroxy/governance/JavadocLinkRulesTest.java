package eu.ciechanowiec.aroxy.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The link resolver reports a resolvable type left as prose or as {@code @code}, accepts one already
 * linked, and reads past the regions where a name needs no link of its own.
 */
class JavadocLinkRulesTest {

    private static final Set<String> RESOLVE = Set.of("Vault", "Base64", "Settings", "IOException");

    private static List<String> unlinked(CharSequence source) {
        return JavadocLinkRules.unlinked(source, RESOLVE::contains);
    }

    /**
     * Wraps {@code body} in a Javadoc comment on a throwaway class. A text block keeps the opening and
     * closing markers on separate lines, so the rule that forbids a single-line Javadoc does not read
     * this fixture as one.
     */
    private static String document(String body) {
        return """
            /**
             * %s
             */
            final class X {}""".formatted(body);
    }

    @Test
    void flagsAResolvableTypeLeftAsProse() {
        assertEquals(List.of("Vault"), unlinked(document("The Vault stores every token.")), "prose must link");
    }

    @Test
    void flagsAResolvableTypeWrittenAsCodeRatherThanLinked() {
        assertEquals(
            List.of("Base64"), unlinked(document("The original is {@code Base64}-encoded.")),
            "@code is for what a link cannot reach, not for a type that resolves"
        );
    }

    @Test
    void acceptsATypeThatIsAlreadyLinked() {
        assertTrue(unlinked(document("The {@link Vault} stores every token.")).isEmpty(), "a link is the point");
    }

    @Test
    void acceptsAnAdjectivalMentionOnceItIsLinked() {
        assertTrue(
            unlinked(document("The original is {@link Base64}-encoded.")).isEmpty(),
            "a type used as an adjective still resolves, so linking it is both possible and required"
        );
    }

    @Test
    void ignoresANameThatDoesNotResolveFromThisFile() {
        assertTrue(
            unlinked(document("Talks to Anthropic over HTTPS and returns JSON.")).isEmpty(),
            "a word is only a type reference when the file could resolve it"
        );
    }

    @Test
    void readsPastTheTargetOfATagTheJavadocToolLinksItself() {
        assertTrue(
            unlinked(document("Reads it.\n * @throws IOException when the file is unreadable")).isEmpty(),
            "@throws links its own target, so wrapping it would be wrong rather than missing"
        );
    }

    @Test
    void readsPastACodeSample() {
        assertTrue(
            unlinked(document("Usage:\n * <pre>\n * Vault vault = new Vault();\n * </pre>")).isEmpty(),
            "a sample is code, and code names types without linking them"
        );
    }

    @Test
    void reportsEachNameOnceEvenWhenRepeated() {
        assertEquals(
            List.of("Settings"), unlinked(document("Settings win last. Settings start from defaults.")),
            "one fix per name, not one per mention"
        );
    }
}
