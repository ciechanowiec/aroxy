#!/usr/bin/env sh
# Lints an AsciiDoc document with the three-engine linter that README.adoc documents:
# .docs/lib/adoc_lint.py runs Vale (prose and typography), a structural linter, and an Asciidoctor
# render pass, and merges their findings into one report. It uses the documented local toolchain so
# source verification can also call pdftotext and tesseract. A non-zero exit means the linter reported
# at least one problem.
set -eu

root="$(git rev-parse --show-toplevel)"
doc="${1:-README.adoc}"

if [ ! -f "$root/$doc" ]; then
    echo "ERROR: $doc not found at the repository root; the project README must exist." >&2
    exit 1
fi

cd "$root"
exec python3 .docs/lib/adoc_lint.py "$doc"
