package eu.ciechanowiec.aroxy.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The rule reads prose and nothing else. Several cases below are places where a semicolon is syntax
 * rather than punctuation, and reporting one would push an author to break working markup or a code
 * sample. The rest pin the comment forms the scan covers and the {@code @return} tag it treats as a
 * fragment. The fixtures are expanded comments because this project does not allow a Javadoc written on
 * one line.
 */
class CommentProseRulesTest {

    @Test
    void reportsASemicolonJoiningTwoClauses() {
        List<String> found = CommentProseRules.semicolons(
            """
                /**
                 * Starts the proxy; returns once it is bound.
                 */
                class A {}
                """
        );
        assertEquals(List.of("Starts the proxy; returns once it is bound."), found);
    }

    @Test
    void readsALineCommentAndABlockCommentToo() {
        List<String> found = CommentProseRules.semicolons(
            """
                class A {
                    // Skip it; the caller retries.
                    /* Binds late; the port may move. */
                    void m() {}
                }
                """
        );
        assertEquals(
            List.of("Skip it; the caller retries.", "Binds late; the port may move."), found
        );
    }

    // A URL is the case that makes tokenizing literals worth the trouble: read by a lone comment
    // pattern, everything after the scheme looks like a line comment ending in a semicolon.
    @Test
    void doesNotReadAStringLiteralAsALineComment() {
        assertTrue(
            CommentProseRules.semicolons(
                """
                    class A {
                        static final String U = "https://example.test/a";
                    }
                    """
            ).isEmpty(),
            "the tail of a URL is not a comment"
        );
    }

    @Test
    void readsACommentThatQuotesAQuotationMark() {
        List<String> found = CommentProseRules.semicolons(
            """
                class A {
                    // The "host" key is optional; the port is not.
                    void m() {}
                }
                """
        );
        assertEquals(List.of("The \"host\" key is optional; the port is not."), found);
    }

    @Test
    void readsPastACodeSampleAndAnInlineTag() {
        assertTrue(
            CommentProseRules.semicolons(
                """
                    /**
                     * <pre>int x = 1;</pre>
                     * A field {@code private int y;} here.
                     */
                    class A {}
                    """
            ).isEmpty(),
            "a semicolon inside a sample or an inline tag is code, not punctuation"
        );
    }

    @Test
    void readsPastAnHtmlEntity() {
        assertTrue(
            CommentProseRules.semicolons(
                """
                    /**
                     * Wraps &lt;body&gt; and &#64;home safely.
                     */
                    class A {}
                    """
            ).isEmpty(),
            "an entity ends in a semicolon by construction"
        );
    }

    @Test
    void ignoresCodeOutsideACommentEntirely() {
        assertTrue(
            CommentProseRules.semicolons("class A { int x = 1; void m() { call(); } }").isEmpty(),
            "ordinary statements are not prose"
        );
    }

    @Test
    void reportsEachOffendingLineOnceAcrossSeveralComments() {
        List<String> found = CommentProseRules.semicolons(
            """
                /**
                 * One; two.
                 */
                class A {}
                /**
                 * One; two.
                 */
                class B {}
                /**
                 * Three; four.
                 */
                class C {}
                """
        );
        assertEquals(List.of("One; two.", "Three; four."), found);
    }

    @Test
    void allowsASemicolonInsideAReturnTag() {
        assertTrue(
            CommentProseRules.semicolons(
                """
                    /**
                     * Masks a value.
                     *
                     * @param value the value
                     * @return the masked rendering; fully hidden when the value is too short
                     */
                    String mask(String value);
                    """
            ).isEmpty(),
            "a fragment has no full stop to reach for, so a semicolon is what joins its second clause"
        );
    }

    // A Justification is a comment written as an annotation, so it is held to the comment guideline. Left
    // out, moving a reason from a comment into the annotation beside it would silence the rule.
    @Test
    void readsAJustificationValueAsProse() {
        List<String> found = CommentProseRules.semicolons(
            """
                class A {
                    @Justification(
                        "a value class; cannot be a record"
                    )
                    @SuppressWarnings("ClassCanBeRecord")
                    void m() {}
                }
                """
        );
        assertEquals(List.of("a value class; cannot be a record"), found);
    }

    // Only the annotation's own value is prose. Every other literal in the file is data, and reading one
    // would report the tail of any URL a field happens to hold.
    @Test
    void readsNoOtherStringLiteralAsProse() {
        assertTrue(
            CommentProseRules.semicolons(
                """
                    class A {
                        static final String S = "one; two";
                    }
                    """
            ).isEmpty(),
            "an ordinary literal is data rather than a sentence"
        );
    }

    @Test
    void reportsAFullStopInsideAReturnTag() {
        List<String> found = CommentProseRules.returnPeriods(
            """
                /**
                 * Masks a value.
                 *
                 * @return the masked rendering. Fully hidden when the value is too short
                 */
                String mask(String value);
                """
        );
        assertEquals(List.of("the masked rendering. Fully hidden when the value is too short"), found);
    }

    @Test
    void endsAReturnTagAtTheNextTag() {
        assertTrue(
            CommentProseRules.returnPeriods(
                """
                    /**
                     * Reads a file.
                     *
                     * @return the parsed file
                     * @throws IOException when the file cannot be read. Ever.
                     */
                    String read();
                    """
            ).isEmpty(),
            "a later tag's prose belongs to that tag, not to the @return above it"
        );
    }

    @Test
    void readsPastAnInlineTagInsideAReturnTag() {
        assertTrue(
            CommentProseRules.returnPeriods(
                """
                    /**
                     * Reads a ratio.
                     *
                     * @return the ratio, at most {@code 1.0}
                     */
                    double ratio();
                    """
            ).isEmpty(),
            "a decimal point inside an inline tag is not a sentence ending"
        );
    }
}
