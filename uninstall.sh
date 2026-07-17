#!/usr/bin/env bash
#
# aroxy uninstaller (macOS + Linux). The exact reverse of install.sh.
#
# What it does, idempotently and with clear logging:
#   1. Stops the running service (graceful, then a pid fallback).
#   2. Removes the launcher ~/.local/bin/aroxy.
#   3. Removes the managed block install.sh added to your shell rc.
#   4. Removes the whole state directory ~/.aroxy (jar, config, vault, logs).
#
# It always removes everything, so there is no separate purge flag, and an
# install -> uninstall -> install cycle starts clean. Running it when nothing
# is installed is safe and prints "Not present" lines.

set -euo pipefail

readonly AROXY_HOME="${AROXY_HOME:-$HOME/.aroxy}"
readonly BIN_DIR="$HOME/.local/bin"
readonly LAUNCHER="$BIN_DIR/aroxy"
readonly INSTALLED_JAR="$AROXY_HOME/aroxy.jar"
readonly PID_FILE="$AROXY_HOME/aroxy.pid"
readonly BLOCK_START="# >>> aroxy >>>"
readonly BLOCK_END="# <<< aroxy <<<"
readonly STOP_WAIT_ATTEMPTS=50
readonly STOP_WAIT_PAUSE=0.1

log() { printf '[aroxy-uninstall] %s\n' "$1" >&2; }

# Polls until the given pid is no longer alive, or ~5s elapses, whichever comes first.
await_exit() {
  local pid="$1" attempt=0
  while [[ "$attempt" -lt "$STOP_WAIT_ATTEMPTS" ]] && kill -0 "$pid" 2>/dev/null; do
    sleep "$STOP_WAIT_PAUSE"
    attempt=$((attempt + 1))
  done
}

# Stops a running daemon and does not return until its process is actually gone, so the state
# directory is never removed from under a live daemon. The pid is read BEFORE `aroxy stop` (which
# clears the pid file), so the escalation fallback still works if graceful shutdown does not exit.
stop_service() {
  local pid running="false"
  pid="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    running="true"
  fi
  if [[ -x "$LAUNCHER" ]]; then
    "$LAUNCHER" stop >/dev/null 2>&1 || true
  elif [[ -f "$INSTALLED_JAR" ]]; then
    java -jar "$INSTALLED_JAR" stop >/dev/null 2>&1 || true
  fi
  [[ "$running" == "true" ]] || return 0
  await_exit "$pid"
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null || true
    await_exit "$pid"
  fi
  if kill -0 "$pid" 2>/dev/null; then
    kill -9 "$pid" 2>/dev/null || true
  fi
  log "Stopped the running service (pid $pid)."
}

remove() {
  local path="$1"
  if [[ -e "$path" ]]; then
    rm -f "$path"
    log "Removed: $path"
  else
    log "Not present: $path"
  fi
}

remove_dir() {
  local path="$1"
  if [[ -d "$path" ]]; then
    rm -rf "$path"
    log "Removed: $path"
  else
    log "Not present: $path"
  fi
}

remove_block() {
  local rc="$1"
  [[ -f "$rc" ]] || return 0
  if grep -qF "$BLOCK_START" "$rc" 2>/dev/null; then
    sed -i.aroxy-bak "/$BLOCK_START/,/$BLOCK_END/d" "$rc"
    rm -f "$rc.aroxy-bak"
    log "Removed aroxy block from: $rc"
  fi
}

main() {
  if [[ "$#" -gt 0 ]]; then
    log "uninstall.sh takes no arguments (it always removes everything); got: $1"
    exit 2
  fi

  stop_service
  remove "$LAUNCHER"
  remove_block "$HOME/.zshrc"
  remove_block "$HOME/.bashrc"
  remove_block "$HOME/.profile"
  remove_dir "$AROXY_HOME"

  log "Done. Restart your shell so the removed PATH and completion take effect."
}

main "$@"
