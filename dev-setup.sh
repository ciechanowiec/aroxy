#!/usr/bin/env sh
# One-time developer setup: point git at the committed hooks in .githooks so the governance checks run
# locally on commit and push. Safe to re-run. CI enforces the same checks regardless, so this is a
# convenience and an early local guard, not the sole line of defense.
set -eu
cd "$(git rev-parse --show-toplevel)"
git config core.hooksPath .githooks
chmod +x .githooks/commit-msg .githooks/pre-commit .githooks/pre-push
echo "Configured core.hooksPath=.githooks; the commit-msg, pre-commit, and pre-push hooks are active."
