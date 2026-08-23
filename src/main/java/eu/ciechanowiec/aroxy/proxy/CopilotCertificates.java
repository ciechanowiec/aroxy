package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.airness.Justification;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.operator.OperatorCreationException;

/**
 * Persistent private certificate authority for Copilot's process-local HTTPS interception. The root
 * is generated once under the owner-only aroxy state directory. Exact-host leaf certificates are
 * added on demand, which covers GitHub's dynamically selected Individual, Business, Enterprise, and
 * data-residency endpoints without granting trust to an unrelated hostname.
 */
final class CopilotCertificates {

    private static final String STORE_TYPE = "PKCS12";
    private static final String AUTHORITY_ALIAS = "aroxy-authority";
    private static final String AUTHORITY_FILE = "copilot-ca.pem";
    private static final String KEY_STORE_FILE = "copilot-ca.p12";
    private static final String PASSWORD = "local-aroxy";
    private static final int PEM_LINE_LENGTH = 64;
    private final Path stateDirectory;
    private final KeyStore store;
    private final ReentrantLock lock;

    private CopilotCertificates(Path stateDirectory, KeyStore store) {
        this.stateDirectory = stateDirectory;
        this.store = store;
        this.lock = new ReentrantLock();
    }

    static CopilotCertificates open(Path stateDirectory) {
        try {
            createAuthorityIfAbsent(stateDirectory);
            return new CopilotCertificates(stateDirectory, load(stateDirectory));
        } catch (IOException | GeneralSecurityException | OperatorCreationException exception) {
            throw new UncheckedIOException("Could not prepare the Copilot certificate authority", asIo(exception));
        }
    }

    static String password() {
        return PASSWORD;
    }

    KeyStore ensure(String host) {
        this.lock.lock();
        try {
            if (this.needsIssue(host)) {
                this.issue(host);
            }
            return this.store;
        } catch (GeneralSecurityException | IOException | OperatorCreationException exception) {
            throw new UncheckedIOException("Could not issue a Copilot certificate for " + host, asIo(exception));
        } finally {
            this.lock.unlock();
        }
    }

    @Justification(
        "the key-store boundary intentionally exposes its common checked security abstraction"
    )
    @SuppressWarnings(
        "OverlyBroadThrowsClause"
    )
    private boolean needsIssue(String host) throws GeneralSecurityException {
        if (!this.store.containsAlias(host)) {
            return true;
        }
        X509Certificate certificate = (X509Certificate) this.store.getCertificate(host);
        List<String> usage = Optional.ofNullable(certificate.getExtendedKeyUsage()).orElse(List.of());
        return !usage.contains(KeyPurposeId.id_kp_serverAuth.getId());
    }

    private static void createAuthorityIfAbsent(
        Path stateDirectory
    ) throws IOException, GeneralSecurityException, OperatorCreationException {
        if (Files.notExists(keyStore(stateDirectory))) {
            createAuthority(stateDirectory);
        }
        if (Files.notExists(authority(stateDirectory))) {
            writeAuthority(stateDirectory, load(stateDirectory));
        }
    }

    @Justification(
        "the key-store boundary intentionally exposes its common checked security abstraction"
    )
    @SuppressWarnings(
        "OverlyBroadThrowsClause"
    )
    private static void createAuthority(
        Path stateDirectory
    ) throws IOException, GeneralSecurityException, OperatorCreationException {
        KeyStore created = KeyStore.getInstance(STORE_TYPE);
        created.load(null, passwordChars());
        CertificateMaterial authority = CopilotCertificateFactory.authority();
        created.setKeyEntry(
            AUTHORITY_ALIAS, authority.signer(), passwordChars(),
            new Certificate[] {authority.certificate()}
        );
        save(stateDirectory, created);
        writeAuthority(stateDirectory, created);
    }

    @Justification(
        "the key-store boundary intentionally exposes its common checked security abstraction"
    )
    @SuppressWarnings(
        "OverlyBroadThrowsClause"
    )
    private static KeyStore load(Path stateDirectory) throws IOException, GeneralSecurityException {
        KeyStore loaded = KeyStore.getInstance(STORE_TYPE);
        try (InputStream input = Files.newInputStream(keyStore(stateDirectory))) {
            loaded.load(input, passwordChars());
        }
        return loaded;
    }

    @Justification(
        "the key-store boundary intentionally exposes its common checked security abstraction"
    )
    @SuppressWarnings(
        "OverlyBroadThrowsClause"
    )
    private void issue(String host) throws IOException, GeneralSecurityException, OperatorCreationException {
        X509Certificate issuer = (X509Certificate) this.store.getCertificate(AUTHORITY_ALIAS);
        PrivateKey issuerKey = (PrivateKey) this.store.getKey(AUTHORITY_ALIAS, passwordChars());
        CertificateMaterial leaf = CopilotCertificateFactory.leaf(host, issuer, issuerKey);
        this.store.setKeyEntry(
            host, leaf.signer(), passwordChars(), new Certificate[] {leaf.certificate(), issuer}
        );
        save(this.stateDirectory, this.store);
    }

    private static Path authority(Path stateDirectory) {
        return stateDirectory.resolve(AUTHORITY_FILE);
    }

    private static Path keyStore(Path stateDirectory) {
        return stateDirectory.resolve(KEY_STORE_FILE);
    }

    @Justification(
        "the key-store boundary intentionally exposes its common checked security abstraction"
    )
    @SuppressWarnings(
        "OverlyBroadThrowsClause"
    )
    private static void save(Path stateDirectory, KeyStore saved) throws IOException, GeneralSecurityException {
        try (OutputStream output = Files.newOutputStream(keyStore(stateDirectory))) {
            saved.store(output, passwordChars());
        }
    }

    @Justification(
        "the key-store boundary intentionally exposes its common checked security abstraction"
    )
    @SuppressWarnings(
        "OverlyBroadThrowsClause"
    )
    private static void writeAuthority(
        Path stateDirectory, KeyStore source
    ) throws IOException, GeneralSecurityException {
        X509Certificate certificate = (X509Certificate) source.getCertificate(AUTHORITY_ALIAS);
        String encoded = Base64.getMimeEncoder(PEM_LINE_LENGTH, new byte[] {'\n'})
            .encodeToString(certificate.getEncoded());
        String pem = "-----BEGIN CERTIFICATE-----\n" + encoded + "\n-----END CERTIFICATE-----\n";
        Files.writeString(authority(stateDirectory), pem, StandardCharsets.US_ASCII);
    }

    private static char[] passwordChars() {
        return PASSWORD.toCharArray();
    }

    private static IOException asIo(Exception exception) {
        return new IOException(exception);
    }
}
