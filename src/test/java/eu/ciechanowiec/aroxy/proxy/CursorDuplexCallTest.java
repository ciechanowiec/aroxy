package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.io.InputStream;
import org.eclipse.jetty.io.Content;
import org.junit.jupiter.api.Test;

class CursorDuplexCallTest {

    @Test
    void abortFailsTheUpstreamRequestContent() {
        CursorDuplexCall call = new CursorDuplexCall(InputStream.nullInputStream());
        IOException failure = new IOException("upload failed");
        call.abort(failure);
        Content.Chunk failed = call.content().read();
        assertSame(failure, failed.getFailure());
    }
}
