package eu.ciechanowiec.aroxy.proxy;

import lombok.experimental.UtilityClass;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509v3CertificateBuilder;

/**
 * Applies aroxy's certificate profiles to Copilot authority and leaf certificates.
 */
@UtilityClass
final class CopilotCertificateExtensions {

    private static final int AUTHORITY_KEY_USAGE = KeyUsage.keyCertSign | KeyUsage.cRLSign;
    private static final int LEAF_KEY_USAGE = KeyUsage.digitalSignature | KeyUsage.keyEncipherment;

    static void authority(X509v3CertificateBuilder builder) throws CertIOException {
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(AUTHORITY_KEY_USAGE));
    }

    static void leaf(X509v3CertificateBuilder builder, String host) throws CertIOException {
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(LEAF_KEY_USAGE));
        builder.addExtension(
            Extension.extendedKeyUsage, false,
            new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth)
        );
        GeneralName hostName = new GeneralName(GeneralName.dNSName, host);
        builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(hostName));
    }
}
