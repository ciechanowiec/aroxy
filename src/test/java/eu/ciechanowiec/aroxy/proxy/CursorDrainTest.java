package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.client.CursorCli;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Stopping Cursor while an exchange is waiting for upstream EOF.
 */
class CursorDrainTest {

    private static final String RUN = "/agent.v1.AgentService/Run";
    private static final String CONNECT_PROTO = "application/connect+proto";
    private static final String JOIN = "join";
    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final Duration POLL = Duration.ofMillis(1);

    @Test
    @SneakyThrows
    void waitsForAnActiveExchangeBeforeStopping(@TempDir Path logs) {
        byte[] terminal = CursorWire.framed(ConnectJsonFrame.END_STREAM, "{}");
        FakeUpstream upstream = new FakeUpstream(
            CannedReply.connectProtobuf(CursorWire.text(terminal)), ReplyEnding.HELD_OPEN
        );
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        FutureTask<Boolean> exchange = exchange(harness);
        Thread.startVirtualThread(exchange);
        FutureTask<Boolean> shutdown = shutdown(harness);
        Thread stopper = new Thread(shutdown);
        try {
            assertTrue(upstream.awaitResponsePayload(WAIT), "the upstream response remains open");
            stopper.start();
            assertTrue(awaitJoin(stopper), "shutdown waits for the active Cursor exchange");
            assertFalse(shutdown.isDone(), "the listener remains alive while the exchange is active");
            upstream.releaseResponseEof();
            assertTrue(exchange.get(WAIT.toSeconds(), TimeUnit.SECONDS));
            assertTrue(shutdown.get(WAIT.toSeconds(), TimeUnit.SECONDS));
        } finally {
            finish(upstream, exchange, shutdown);
        }
    }

    private static FutureTask<Boolean> exchange(ProxyHarness harness) {
        return new FutureTask<>(
            () -> {
                byte[] body = CursorWire.frame(
                    CursorWire.protobuf(CursorWire.protobuf(ProxyHarness.SECRET))
                );
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(harness.base().resolve(RUN))
                    .header("Content-Type", CONNECT_PROTO)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
                try (HttpClient client = HttpClient.newHttpClient()) {
                    client.send(request, HttpResponse.BodyHandlers.discarding());
                }
                return true;
            }
        );
    }

    private static FutureTask<Boolean> shutdown(ProxyHarness harness) {
        return new FutureTask<>(
            () -> {
                harness.stop();
                return true;
            }
        );
    }

    private static boolean awaitJoin(Thread shutdown) {
        Instant deadline = Instant.now().plus(WAIT);
        return Stream.generate(() -> stack(shutdown))
            .takeWhile(_ -> Instant.now().isBefore(deadline))
            .anyMatch(CursorDrainTest::joinsExchange);
    }

    private static StackTraceElement[] stack(Thread shutdown) {
        LockSupport.parkNanos(POLL.toNanos());
        return shutdown.getStackTrace();
    }

    private static boolean joinsExchange(StackTraceElement... stack) {
        return Arrays.stream(stack).anyMatch(CursorDrainTest::isExchangeJoin);
    }

    private static boolean isExchangeJoin(StackTraceElement frame) {
        return VirtualThreadExchange.class.getName().equals(frame.getClassName())
            && JOIN.equals(frame.getMethodName());
    }

    @SneakyThrows
    private static void finish(
        FakeUpstream upstream, Future<Boolean> exchange, FutureTask<Boolean> shutdown
    ) {
        upstream.releaseResponseEof();
        shutdown.run();
        shutdown.get(WAIT.toSeconds(), TimeUnit.SECONDS);
        exchange.get(WAIT.toSeconds(), TimeUnit.SECONDS);
    }
}
