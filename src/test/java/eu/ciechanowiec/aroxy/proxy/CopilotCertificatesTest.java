package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.daemon.Home;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import lombok.SneakyThrows;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies persistent authority reuse and exact-host leaf issuance.
 */
class CopilotCertificatesTest {

    private static final String HOST = "api.business.githubcopilot.com";

    @TempDir
    private Path base;

    @Test
    @SneakyThrows
    void createsThePrivateStoreAndPublicAuthority() {
        Home home = new Home(this.base);
        CopilotCertificates.open(home).ensure(HOST);
        assertTrue(Files.isRegularFile(home.base().resolve("copilot-ca.p12")));
        assertTrue(Files.readString(home.base().resolve("copilot-ca.pem")).contains("BEGIN CERTIFICATE"));
    }

    @Test
    @SneakyThrows
    void issuesAnExactHostCertificateAndReusesIt() {
        Home home = new Home(this.base);
        KeyStore firstStore = CopilotCertificates.open(home).ensure(HOST);
        X509Certificate first = (X509Certificate) firstStore.getCertificate(HOST);
        KeyStore reopened = CopilotCertificates.open(home).ensure(HOST);
        X509Certificate second = (X509Certificate) reopened.getCertificate(HOST);
        assertEquals(first.getSerialNumber(), second.getSerialNumber());
        assertTrue(first.getSubjectAlternativeNames().stream().anyMatch(name -> name.contains(HOST)));
        assertTrue(first.getExtendedKeyUsage().contains(KeyPurposeId.id_kp_serverAuth.getId()));
        first.verify(reopened.getCertificate("aroxy-authority").getPublicKey());
    }

    @Test
    @SneakyThrows
    void replacesALeafWithoutServerAuthenticationUsage() {
        Home home = new Home(this.base);
        KeyStore store = CopilotCertificates.open(home).ensure(HOST);
        Certificate authority = store.getCertificate("aroxy-authority");
        Key authorityKey = store.getKey("aroxy-authority", CopilotCertificates.password().toCharArray());
        store.setKeyEntry(
            HOST, authorityKey, CopilotCertificates.password().toCharArray(),
            new Certificate[] {authority}
        );
        try (OutputStream output = Files.newOutputStream(home.base().resolve("copilot-ca.p12"))) {
            store.store(output, CopilotCertificates.password().toCharArray());
        }
        X509Certificate migrated = (X509Certificate) CopilotCertificates.open(home)
            .ensure(HOST)
            .getCertificate(HOST);
        assertNotEquals(((X509Certificate) authority).getSerialNumber(), migrated.getSerialNumber());
        assertTrue(migrated.getExtendedKeyUsage().contains(KeyPurposeId.id_kp_serverAuth.getId()));
    }

    @Test
    @SneakyThrows
    void restoresThePublicAuthorityFromThePrivateStore() {
        Home home = new Home(this.base);
        CopilotCertificates.open(home);
        Path authority = home.base().resolve("copilot-ca.pem");
        Files.delete(authority);
        CopilotCertificates.open(home);
        assertTrue(Files.readString(authority).contains("BEGIN CERTIFICATE"));
    }

    @Test
    @SneakyThrows
    void reportsAnUnreadablePrivateStoreAsAnIoFailure() {
        Home home = new Home(this.base);
        Files.writeString(home.base().resolve("copilot-ca.p12"), "not a key store");
        assertThrows(UncheckedIOException.class, () -> CopilotCertificates.open(home));
    }
}
