#!/usr/bin/env sh
# Lints an AsciiDoc document with the three-engine linter that README.adoc documents:
# .docs/lib/adoc_lint.py runs Vale (prose and typography), a structural linter, and an Asciidoctor
# render pass, and merges their findings into one report. It runs inside the pinned Vale image, which
# already carries Vale, Asciidoctor, and Python 3, so no local install of any of them is needed and the
# versions are the same everywhere. The working tree is mounted read-write because the linter writes a
# short-lived temporary file next to the document so that Vale resolves .vale.ini from the same
# directory. A non-zero exit means the linter reported at least one problem.
set -eu

root="$(git rev-parse --show-toplevel)"
image="jdkato/vale:v3.15.1"
doc="${1:-README.adoc}"

if [ ! -f "$root/$doc" ]; then
    echo "ERROR: $doc not found at the repository root; the project README must exist." >&2
    exit 1
fi

docker run --rm \
    -v "$root":/repo \
    -w /repo \
    --entrypoint python3 \
    "$image" \
    .docs/lib/adoc_lint.py "$doc"
