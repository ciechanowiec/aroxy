package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.client.BufferedTransport;
import eu.ciechanowiec.aroxy.client.ClientId;
import eu.ciechanowiec.aroxy.client.ClientProfile;
import eu.ciechanowiec.aroxy.client.CodexCli;
import eu.ciechanowiec.aroxy.client.CopilotTransport;
import eu.ciechanowiec.aroxy.client.Gateway;
import eu.ciechanowiec.aroxy.client.PathScope;
import eu.ciechanowiec.aroxy.client.RequestBodyPolicy;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * The localhost-only Copilot profile shared by socket-level tests, together with the client that can
 * actually reach it. Copilot is the one client reached through CONNECT, so a test of it needs an
 * {@link HttpClient} that both trusts the authority the proxy minted and treats the proxy as a proxy
 * rather than as an origin. Building that is the same eleven lines every time, so it is written here
 * once: a test that copied it would be pinning the plumbing rather than the behaviour it is named for.
 */
@UtilityClass
final class CopilotFixtures {

    private static final String CERTIFICATE_FILE = "copilot-ca.pem";
    private static final String RESPONSE_DUMP = "-raw-response.txt";

    static ClientProfile profile() {
        BufferedTransport responses = CodexCli.transport();
        CopilotTransport transport = new CopilotTransport(
            Set.of("localhost"), RequestBodyPolicy.jsonExcept(Set.of("encrypted_content")),
            responses, responses
        );
        return new ClientProfile(
            new ClientId("copilot-test"), "Copilot test",
            new Gateway(0, URI.create("https://api.githubcopilot.com")),
            new PathScope(List.of("/responses", "/chat/completions"), "Copilot inference"), transport
        );
    }

    /**
     * An HTTP client trusting the proxy's own authority and routed through the proxy at {@code proxied}.
     */
    @SneakyThrows
    static HttpClient client(URI proxied, Path base) {
        KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
        trust.load(null);
        trust.setCertificateEntry("aroxy", authority(base));
        TrustManagerFactory managers = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        );
        managers.init(trust);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, managers.getTrustManagers(), null);
        InetSocketAddress address = new InetSocketAddress(proxied.getHost(), proxied.getPort());
        return HttpClient.newBuilder().sslContext(context).proxy(ProxySelector.of(address)).build();
    }

    @SneakyThrows
    static Certificate authority(Path base) {
        try (InputStream input = Files.newInputStream(base.resolve(CERTIFICATE_FILE))) {
            return CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
    }

    @SneakyThrows
    static List<Path> wireFiles(Path base) {
        try (Stream<Path> files = Files.list(base.resolve("wire"))) {
            return files.filter(path -> path.getFileName().toString().endsWith(RESPONSE_DUMP)).toList();
        }
    }
}
