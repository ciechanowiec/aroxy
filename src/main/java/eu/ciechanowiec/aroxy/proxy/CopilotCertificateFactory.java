package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.airness.Justification;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import lombok.experimental.UtilityClass;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Issues the root and exact-host certificates used by the Copilot interception boundary.
 */
@UtilityClass
final class CopilotCertificateFactory {

    private static final String AUTHORITY_NAME = "CN=aroxy Copilot Local CA";
    private static final String CURVE = "secp256r1";
    private static final String KEY_ALGORITHM = "EC";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    private static final int SERIAL_BITS = 159;
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);
    private static final Duration ROOT_VALIDITY = Duration.ofDays(3650);
    private static final Duration LEAF_VALIDITY = Duration.ofDays(397);
    private static final BouncyCastleProvider PROVIDER = new BouncyCastleProvider();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Justification(
        "the provider boundary intentionally exposes its common checked security abstraction"
    )
    @SuppressWarnings(
        "OverlyBroadThrowsClause"
    )
    static CertificateMaterial authority() throws IOException, GeneralSecurityException, OperatorCreationException {
        KeyPair keys = keyPair();
        X500Name name = new X500Name(AUTHORITY_NAME);
        X509v3CertificateBuilder builder = certificateBuilder(name, name, keys, ROOT_VALIDITY);
        CopilotCertificateExtensions.authority(builder);
        return new CertificateMaterial(sign(builder, keys.getPrivate()), keys.getPrivate());
    }

    @Justification(
        "the provider boundary intentionally exposes its common checked security abstraction"
    )
    @SuppressWarnings(
        "OverlyBroadThrowsClause"
    )
    static CertificateMaterial leaf(
        String host, X509Certificate issuer, PrivateKey issuerKey
    ) throws IOException, GeneralSecurityException, OperatorCreationException {
        KeyPair keys = keyPair();
        X500Name issuerName = X500Name.getInstance(issuer.getSubjectX500Principal().getEncoded());
        X500Name subjectName = new X500Name("CN=" + host);
        X509v3CertificateBuilder builder = certificateBuilder(
            issuerName, subjectName, keys, LEAF_VALIDITY
        );
        CopilotCertificateExtensions.leaf(builder, host);
        return new CertificateMaterial(sign(builder, issuerKey), keys.getPrivate());
    }

    private static X509v3CertificateBuilder certificateBuilder(
        X500Name issuer, X500Name subject, KeyPair subjectKeys, Duration validity
    ) {
        Instant now = Instant.now();
        return new JcaX509v3CertificateBuilder(
            issuer, serial(), Date.from(now.minus(CLOCK_SKEW)), Date.from(now.plus(validity)),
            subject, subjectKeys.getPublic()
        );
    }

    @Justification(
        "the provider boundary intentionally exposes its common checked security abstraction"
    )
    @SuppressWarnings(
        "OverlyBroadThrowsClause"
    )
    private static X509Certificate sign(
        X509v3CertificateBuilder builder, PrivateKey issuerKey
    ) throws GeneralSecurityException, OperatorCreationException {
        return new JcaX509CertificateConverter()
            .setProvider(PROVIDER)
            .getCertificate(
                builder.build(
                    new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                        .setProvider(PROVIDER)
                        .build(issuerKey)
                )
            );
    }

    @Justification(
        "the provider boundary intentionally exposes its common checked security abstraction"
    )
    @SuppressWarnings(
        "OverlyBroadThrowsClause"
    )
    private static KeyPair keyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM, PROVIDER);
        generator.initialize(new ECGenParameterSpec(CURVE), RANDOM);
        return generator.generateKeyPair();
    }

    private static BigInteger serial() {
        return new BigInteger(SERIAL_BITS, RANDOM).setBit(0);
    }
}
