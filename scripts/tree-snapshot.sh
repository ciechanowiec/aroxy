#!/usr/bin/env sh
# Fails the build when the build itself edits the working tree.
#
# snapshot (bound to validate) records a content fingerprint of every tracked file plus every untracked
# file that is not ignored; verify (bound to package) takes the same fingerprint again and compares, so
# work you had already edited sits in the first fingerprint and cannot fail the build. Ignored paths,
# target/ above all, are excluded.
#
# What it reports is a change made between those two points, by anything at all: it cannot tell the build
# apart from an editor saving a file while the build runs, and a file written that way is blamed on the
# build. That is the price of knowing nothing about who wrote to the tree, and it is the right trade for
# a net whose whole job is to catch writers nobody anticipated.
#
# This is the tool-agnostic net behind the check-only goals of OpenRewrite, the formatter and the import
# sorter: it knows nothing about who wrote to the tree, so it also covers a script that strands a
# temporary file and any future plugin that edits sources without being asked. The pom explains the rule
# the two halves serve; a build that means to reshape sources runs with -Pformat, which unbinds verify.
set -eu

mode="${1:-}"
# Constant within one Maven invocation and different between invocations. OpenRewrite forks a lifecycle
# of its own, which re-runs validate; the token lets that second call recognize its own build and leave
# the first fingerprint alone rather than retake it mid-build. Keying on the token rather than on the
# file's mere existence keeps a build run without clean from comparing against the previous build.
build_token="${2:-}"
case "$mode" in
    snapshot | verify) ;;
    *)
        echo "usage: $0 snapshot|verify [build-token]" >&2
        exit 1
        ;;
esac

if ! root="$(git rev-parse --show-toplevel 2>/dev/null)"; then
    echo "NOTE: not a git work tree, so the tree-mutation check is skipped." >&2
    exit 0
fi

fingerprint_file="$root/target/tree-fingerprint.txt"
token_file="$root/target/tree-fingerprint.token"

# "<64 hex>  <path>" per file, ordered by path. A tracked file the build deleted simply drops out of the
# list, and a file the build created appears in it, so both show up in the comparison as a changed path.
fingerprint() {
    cd "$root"
    {
        git ls-files -z
        git ls-files -z --others --exclude-standard
    } | xargs -0 shasum -a 256 2>/dev/null | LC_ALL=C sort -k2
}

if [ "$mode" = "snapshot" ]; then
    if [ -n "$build_token" ] && [ -f "$token_file" ] && [ "$(cat "$token_file")" = "$build_token" ]; then
        # A forked lifecycle re-entered validate; the fingerprint of this build is already taken.
        exit 0
    fi
    mkdir -p "$(dirname "$fingerprint_file")"
    fingerprint >"$fingerprint_file"
    printf '%s\n' "$build_token" >"$token_file"
    exit 0
fi

if [ ! -f "$fingerprint_file" ]; then
    echo "ERROR: no fingerprint from the validate phase at $fingerprint_file." >&2
    echo "       Run the whole lifecycle (for example mvn package), not the package phase alone." >&2
    exit 1
fi

current="$(mktemp)"
before="$(mktemp)"
after="$(mktemp)"
# shellcheck disable=SC2064 # the paths are expanded now on purpose, while they are still in scope
trap "rm -f '$current' '$before' '$after'" EXIT
fingerprint >"$current"

LC_ALL=C sort "$fingerprint_file" >"$before"
LC_ALL=C sort "$current" >"$after"

# A sha256 is 64 hex characters and shasum separates it from the path by two spaces, so the path starts
# at column 67. comm -3 prefixes lines unique to the second file with a tab.
changed="$(comm -3 "$before" "$after" | sed 's/^\t//' | cut -c67- | LC_ALL=C sort -u)"

if [ -z "$changed" ]; then
    exit 0
fi

echo "ERROR: the build modified the working tree. A build that verifies must not write to it." >&2
echo "" >&2
echo "Files the build changed, created or deleted:" >&2
echo "$changed" | sed 's/^/    /' >&2
echo "" >&2
echo "If a source-shaping tool did this on purpose, apply it explicitly:" >&2
echo "    mvn process-resources -Pformat" >&2
echo "and commit the result. Otherwise the tool that wrote to the tree needs a check-only goal." >&2
exit 1
