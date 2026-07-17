#!/usr/bin/env sh
# Runs Qodana (the IntelliJ inspection engine) over the project via Docker and enforces the committed
# inspection profile. Behind a TLS-inspecting proxy the container must trust the proxy's root CA; on
# macOS this script exports the host's already-trusted roots and appends them to the container's trust
# store. On a clean network (CI) that export is empty or a harmless superset, so the same script works
# everywhere. Results, including qodana.sarif.json, land in target/qodana. A non-zero exit means Qodana
# found at least one problem.
#
# The image is passed in so it is pinned where every other pinned version lives, the pom.
set -eu

image="${1:?usage: $0 <qodana-image>}"
root="$(git rev-parse --show-toplevel)"
results="$root/target/qodana"
ca="$root/target/qodana-ca.pem"
rm -rf "$results"
mkdir -p "$results"

# Export the host-trusted roots (macOS keychains) so the container trusts the same CAs the host does,
# including any corporate TLS-inspection CA. On Linux CI 'security' is absent and the bundle stays empty.
: > "$ca"
if command -v security >/dev/null 2>&1; then
    security find-certificate -a -p /System/Library/Keychains/SystemRootCertificates.keychain >> "$ca" 2>/dev/null || true
    security find-certificate -a -p /Library/Keychains/System.keychain >> "$ca" 2>/dev/null || true
fi

docker run --rm \
    -v "$root":/data/project \
    -v "$results":/data/results \
    -v "$ca":/opt/hostca.pem:ro \
    --entrypoint /bin/sh \
    "$image" \
    -c 'bundle="$(readlink -f /etc/ssl/certs/ca-certificates.crt)"; [ -s /opt/hostca.pem ] && cat /opt/hostca.pem >> "$bundle"; exec /opt/idea/bin/qodana scan --disable-update-checks'
