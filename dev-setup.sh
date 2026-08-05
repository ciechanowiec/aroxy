#!/usr/bin/env sh
# One-time developer setup: point git at the committed hooks in .githooks so verification runs locally
# before a commit and push. Safe to re-run. The hooks are an early convenience rather than the source
# of the rules, which come from Airness and still run through Maven.
set -eu
cd "$(git rev-parse --show-toplevel)"
git config core.hooksPath .githooks
chmod +x .githooks/pre-commit .githooks/pre-push
echo "Configured core.hooksPath=.githooks; the pre-commit and pre-push hooks are active."
