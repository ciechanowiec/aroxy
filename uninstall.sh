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

# Exported so every aroxy JVM this script invokes resolves the same state directory the script
# cleans. A JVM on macOS derives user.home from the account database and ignores HOME, so without
# the export a run under an overridden HOME would remove a sandbox while stopping the real daemon.
readonly AROXY_HOME="${AROXY_HOME:-${HOME}/.aroxy}"
export AROXY_HOME
readonly BIN_DIR="${HOME}/.local/bin"
readonly LAUNCHER="${BIN_DIR}/aroxy"
readonly INSTALLED_JAR="${AROXY_HOME}/aroxy.jar"
readonly PID_FILE="${AROXY_HOME}/aroxy.pid"
readonly BLOCK_START="# >>> aroxy >>>"
readonly BLOCK_END="# <<< aroxy <<<"
readonly STOP_WAIT_ATTEMPTS=50
readonly STOP_WAIT_PAUSE=0.1

# Presentation. This mirrors the CLI's own output vocabulary (src/main/java/eu/ciechanowiec/aroxy/cli/
# CliOutput.java) so uninstalling reads like every other aroxy command: a cyan bullet for a step in
# progress, a green check for one that completed, a dim circle for something that was already absent,
# and dim padded labels for the values underneath. It is duplicated from install.sh rather than sourced
# from a shared file on purpose, because both scripts are used standalone, where a sibling file would
# not be there to source. The block is kept identical to the one in install.sh so the copies can be
# compared with a plain diff, which is why it also defines the states this script never reaches.
#
# Unlike install.sh this script requires no JDK. Removal has to work when the JVM that ran the daemon
# is gone, which is what the signal fallback in stop_service is for.
if [[ -t 2 && -z "${NO_COLOR:-}" && "${TERM:-}" != "dumb" ]]; then
  readonly RESET=$'\033[0m' BOLD=$'\033[1m' DIM=$'\033[2m'
  readonly GREEN=$'\033[32m' YELLOW=$'\033[33m' CYAN=$'\033[36m' RED=$'\033[31m'
else
  readonly RESET='' BOLD='' DIM=''
  readonly GREEN='' YELLOW='' CYAN='' RED=''
fi

state() { printf '%s%s%s %s%s%s\n' "$1" "$2" "${RESET}" "${BOLD}" "$3" "${RESET}" >&2; }
success() { state "${GREEN}" '✓' "$1"; }
info() { state "${CYAN}" '●' "$1"; }
inactive() { state "${DIM}" '○' "$1"; }
warning() { state "${YELLOW}" '!' "$1"; }
failure() { state "${RED}" '✗' "$1"; }
heading() { printf '%s%s%s%s\n' "${CYAN}" "${BOLD}" "$1" "${RESET}" >&2; }
detail() { printf '  %s%-12s%s %s\n' "${DIM}" "$1" "${RESET}" "$2" >&2; }
note() { printf '  %s%s%s\n' "${DIM}" "$1" "${RESET}" >&2; }

# Polls until the given pid is no longer alive, or ~5s elapses, whichever comes first.
await_exit() {
  local pid="$1" attempt=0
  while [[ "${attempt}" -lt "${STOP_WAIT_ATTEMPTS}" ]] && kill -0 "${pid}" 2>/dev/null; do
    sleep "${STOP_WAIT_PAUSE}"
    attempt=$((attempt + 1))
  done
}

recorded_pid() {
  local pid=""
  if [[ -f "${PID_FILE}" ]]; then
    IFS=$' \t' read -r pid _ < "${PID_FILE}" || true
  fi
  if [[ "${pid}" =~ ^[0-9]+$ ]]; then
    printf '%s' "${pid}"
  fi
}

# Stops a running daemon and does not return until its process is actually gone, so the state
# directory is never removed from under a live daemon. The pid is read BEFORE `aroxy stop` (which
# clears the pid file), so the escalation fallback still works if graceful shutdown does not exit.
stop_service() {
  local pid running="false"
  pid="$(recorded_pid)"
  if [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null; then
    running="true"
  fi
  if [[ -x "${LAUNCHER}" ]]; then
    "${LAUNCHER}" stop >/dev/null 2>&1 || true
  elif [[ -f "${INSTALLED_JAR}" ]]; then
    java -jar "${INSTALLED_JAR}" stop >/dev/null 2>&1 || true
  fi
  [[ "${running}" == "true" ]] || return 0
  await_exit "${pid}"
  if kill -0 "${pid}" 2>/dev/null; then
    kill "${pid}" 2>/dev/null || true
    await_exit "${pid}"
  fi
  if kill -0 "${pid}" 2>/dev/null; then
    kill -9 "${pid}" 2>/dev/null || true
  fi
  success 'Stopped the running service'
  detail 'PID' "${pid}"
}

remove() {
  local path="$1"
  if [[ -e "${path}" ]]; then
    rm -f "${path}"
    success 'Removed the launcher'
    detail 'Path' "${path}"
  else
    inactive 'No launcher to remove'
    detail 'Path' "${path}"
  fi
}

remove_dir() {
  local path="$1"
  if [[ -d "${path}" ]]; then
    rm -rf "${path}"
    success 'Removed the state directory (jar, configuration, vault, logs)'
    detail 'Path' "${path}"
  else
    inactive 'No state directory to remove'
    detail 'Path' "${path}"
  fi
}

remove_block() {
  local rc="$1"
  [[ -f "${rc}" ]] || return 0
  if grep -qF "${BLOCK_START}" "${rc}" 2>/dev/null; then
    sed -i.aroxy-bak "/${BLOCK_START}/,/${BLOCK_END}/d" "${rc}"
    rm -f "${rc}.aroxy-bak"
    success 'Removed the managed shell block'
    detail 'Path' "${rc}"
  fi
}

main() {
  if [[ "$#" -gt 0 ]]; then
    failure 'uninstall.sh takes no arguments, because it always removes everything'
    detail 'Received' "$1"
    exit 2
  fi

  heading 'aroxy uninstaller'
  stop_service
  remove "${LAUNCHER}"
  remove_block "${HOME}/.zshrc"
  remove_block "${HOME}/.bashrc"
  remove_block "${HOME}/.profile"
  remove_dir "${AROXY_HOME}"

  success 'Uninstalled'
  note 'Restart your shell so the removed PATH and completion take effect.'
}

main "$@"
