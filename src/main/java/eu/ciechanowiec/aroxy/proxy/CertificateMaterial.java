package eu.ciechanowiec.aroxy.proxy;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * A private key paired with its certificate.
 *
 * @param certificate certificate
 * @param signer      signing key
 */
record CertificateMaterial(
    X509Certificate certificate,
    PrivateKey signer
) {
}
