package eu.ciechanowiec.aroxy.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The one parsing seam both readers share. The hook reads the message file before git has cleaned it,
 * so the comment block git appends must not count as a body. The whole-history reader gets the cleaned
 * message and must parse to the same header and body from it.
 */
class CommitMessagesTest {

    private static final String HEADER = "refactor(config): split the effective config merge";
    private static final String TEMPLATE = """
        # Please enter the commit message for your changes. Lines starting
        # with '#' will be ignored, and an empty message aborts the commit.
        #
        # On branch main
        # Changes to be committed:
        #\tmodified:   pom.xml
        """;

    @Test
    void readsTheHeaderAndTheBody() {
        CommitMessage message = CommitMessages.parse(HEADER + "\n\nThe merge grew past the cap.\n");
        assertEquals(HEADER, message.header(), "the header is the first line");
        assertEquals("The merge grew past the cap.", message.body(), "the body is what follows");
    }

    @Test
    void theCommentGitAppendsIsNotABody() {
        CommitMessage message = CommitMessages.parse(HEADER + "\n\n" + TEMPLATE);
        assertEquals(HEADER, message.header(), "the header survives the comments");
        assertTrue(
            message.body().isBlank(),
            "a message carrying only git's template declares no body, so the body rule can still fire"
        );
    }

    @Test
    void aRealBodySurvivesAlongsideTheComments() {
        CommitMessage message = CommitMessages.parse(HEADER + "\n\nThe merge grew past the cap.\n\n" + TEMPLATE);
        assertEquals("The merge grew past the cap.", message.body(), "the prose is kept, the comments are not");
    }

    @Test
    void aHeaderWithoutABodyParses() {
        CommitMessage message = CommitMessages.parse(HEADER);
        assertEquals(HEADER, message.header(), "a lone header is the header");
        assertEquals("", message.body(), "and it carries no body");
    }
}
