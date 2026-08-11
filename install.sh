#!/usr/bin/env bash
#
# aroxy installer (macOS + Linux). Works two ways:
#   * From a source checkout: it uses target/aroxy.jar, building it with Maven if absent.
#   * Standalone (curl -fsSL .../install.sh | bash): it downloads the version pinned below
#     from Maven Central.
#
# It always reinstalls from scratch, idempotently and with clear logging:
#   1. When aroxy is already installed, it reports the current version and the new version,
#      stops the service if it is running, and removes the previous state directory ~/.aroxy
#      (configuration, vault, logs, and attachments registry) so the reinstall starts clean --
#      the same end state as running uninstall.sh and then install.sh.
#   2. Puts the jar at ~/.aroxy/aroxy.jar.
#   3. Writes a launcher to ~/.local/bin/aroxy and makes it executable.
#   4. Generates a shell completion script to ~/.aroxy/aroxy_completion.
#   5. Adds a reversible managed block to your shell rc (~/.zshrc or ~/.bashrc) that puts
#      the launcher on PATH and enables completion. Re-running replaces the block; uninstall.sh
#      removes it.
#
# Boundary: aroxy is data minimization, not circumvention. It removes sensitive content from
# what leaves the machine; it does not hide that an agent is used nor change the destination.

set -euo pipefail

readonly AROXY_HOME="${AROXY_HOME:-$HOME/.aroxy}"
readonly BIN_DIR="$HOME/.local/bin"
readonly LAUNCHER="$BIN_DIR/aroxy"
readonly INSTALLED_JAR="$AROXY_HOME/aroxy.jar"
readonly STAGED_JAR="$AROXY_HOME/.aroxy.jar.new"
readonly COMPLETION="$AROXY_HOME/aroxy_completion"
readonly PID_FILE="$AROXY_HOME/aroxy.pid"
readonly RELEASE_VERSION="1.0.1"
readonly MAVEN_RELEASE_BASE="https://repo.maven.apache.org/maven2/eu/ciechanowiec/aroxy/$RELEASE_VERSION"
readonly RELEASE_JAR_URL="$MAVEN_RELEASE_BASE/aroxy-$RELEASE_VERSION.jar"
readonly RELEASE_SHA_URL="$RELEASE_JAR_URL.sha256"
readonly BLOCK_START="# >>> aroxy >>>"
readonly BLOCK_END="# <<< aroxy <<<"
readonly STOP_WAIT_ATTEMPTS=50
readonly STOP_WAIT_PAUSE=0.1

log() { printf '[aroxy-install] %s\n' "$1" >&2; }

script_dir() {
  local src="${BASH_SOURCE[0]:-}"
  [[ -n "$src" ]] || return 1
  cd "$(dirname "$src")" && pwd
}

target_rc() {
  case "${SHELL:-}" in
    */zsh) printf '%s' "$HOME/.zshrc" ;;
    */bash) printf '%s' "$HOME/.bashrc" ;;
    *) printf '%s' "$HOME/.profile" ;;
  esac
}

jar_version() {
  java -jar "$1" --version 2>/dev/null | head -n 1
}

# Polls until the given pid is no longer alive, or ~5s elapses, whichever comes first.
await_exit() {
  local pid="$1" attempt=0
  while [[ "$attempt" -lt "$STOP_WAIT_ATTEMPTS" ]] && kill -0 "$pid" 2>/dev/null; do
    sleep "$STOP_WAIT_PAUSE"
    attempt=$((attempt + 1))
  done
}

recorded_pid() {
  local pid=""
  if [[ -f "$PID_FILE" ]]; then
    IFS=$' \t' read -r pid _ < "$PID_FILE" || true
  fi
  if [[ "$pid" =~ ^[0-9]+$ ]]; then
    printf '%s' "$pid"
  fi
}

# Stops a running daemon and does not return until its process is actually gone, so the caller can
# safely delete the state directory afterwards. The pid is read BEFORE `aroxy stop` (which clears the
# pid file), so the escalation fallback still works if the graceful shutdown fails to exit in time.
stop_service() {
  local pid running="false"
  pid="$(recorded_pid)"
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    running="true"
    log "The service is running (pid $pid) and will be stopped first."
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

acquire_jar() {
  local dest="$1" here
  here="$(script_dir 2>/dev/null || true)"
  if [[ -n "$here" && -f "$here/target/aroxy.jar" ]]; then
    log "Using the locally built jar: $here/target/aroxy.jar"
    cp "$here/target/aroxy.jar" "$dest"
  elif [[ -n "$here" && -f "$here/pom.xml" ]]; then
    log "Building the jar with: mvn -q clean package"
    ( cd "$here" && mvn -q clean package )
    cp "$here/target/aroxy.jar" "$dest"
  else
    log "Downloading aroxy $RELEASE_VERSION from Maven Central..."
    curl -fsSL "$RELEASE_JAR_URL" -o "$dest"
    verify_jar "$dest"
  fi
}

# Computes the SHA-256 of a file, using whichever tool the OS provides (sha256sum on Linux,
# shasum on macOS).
sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

# Verifies a downloaded jar against the SHA-256 published alongside the release, failing closed: a
# missing checksum or a mismatch deletes the download and aborts the install, so a tampered or
# corrupted jar is never put on PATH.
verify_jar() {
  local jar="$1" expected actual version
  log "Verifying the download against its published SHA-256..."
  expected="$(curl -fsSL "$RELEASE_SHA_URL" | tr -d '[:space:]')"
  if [[ ! "$expected" =~ ^[0-9a-fA-F]{64}$ ]]; then
    rm -f "$jar"
    log "ERROR: Maven Central returned no valid SHA-256; aborting."
    exit 1
  fi
  actual="$(sha256_of "$jar")"
  if [[ "$actual" != "$expected" ]]; then
    rm -f "$jar"
    log "ERROR: checksum mismatch (expected $expected, got $actual); aborting."
    exit 1
  fi
  version="$(jar_version "$jar")"
  if [[ "$version" != "aroxy $RELEASE_VERSION" ]]; then
    rm -f "$jar"
    log "ERROR: downloaded jar reports '$version', not 'aroxy $RELEASE_VERSION'; aborting."
    exit 1
  fi
  log "Checksum and version verified."
}

write_launcher() {
  mkdir -p "$BIN_DIR"
  cat > "$LAUNCHER" <<EOF
#!/usr/bin/env bash
exec java -jar "\$HOME/.aroxy/aroxy.jar" "\$@"
EOF
  chmod +x "$LAUNCHER"
  log "Wrote launcher: $LAUNCHER"
}

write_completion() {
  if java -jar "$INSTALLED_JAR" generate-completion > "$COMPLETION" 2>/dev/null; then
    # picocli's generated script only offers flags when the current word starts with '-'. After each
    # else-branch COMPREPLY line (the only ones carrying ${IFS}) append a second compgen for the
    # flags so a bare TAB offers them too. It must run with IFS reset to a space, because the
    # else-branch sets IFS=$'\n' for file positionals while ${flag_opts} is space-separated -- folding
    # them into that newline word list would collapse every flag into one candidate. Appending also
    # fills COMPREPLY on flag-only subcommands like `logs`, suppressing zsh's directory fallback.
    sed -i.aroxy-bak '/\${IFS}/ s|$|\
    COMPREPLY+=( $(IFS=" "; compgen -W "${flag_opts} ${arg_opts}" -- "${curr_word}") )|' "$COMPLETION"
    rm -f "$COMPLETION.aroxy-bak"
    log "Wrote completion (flags complete on bare TAB): $COMPLETION"
  else
    log "WARNING: could not generate completion script (skipping)."
  fi
}

setup_shell() {
  local rc
  rc="$(target_rc)"
  touch "$rc"
  if grep -qF "$BLOCK_START" "$rc" 2>/dev/null; then
    sed -i.aroxy-bak "/$BLOCK_START/,/$BLOCK_END/d" "$rc"
    rm -f "$rc.aroxy-bak"
  fi
  cat >> "$rc" <<'BLOCK'
# >>> aroxy >>>
# Added by aroxy install.sh; removed by uninstall.sh.
export PATH="$HOME/.local/bin:$PATH"
if [ -f "$HOME/.aroxy/aroxy_completion" ]; then
  if [ -n "${ZSH_VERSION:-}" ]; then autoload -U +X bashcompinit 2>/dev/null && bashcompinit 2>/dev/null; fi
  . "$HOME/.aroxy/aroxy_completion"
fi
# Keep trust and proxy settings inside the Copilot process tree. Use `command copilot` to bypass aroxy.
copilot() { command aroxy copilot "$@"; }
# <<< aroxy <<<
BLOCK
  log "Set up PATH and completion in: $rc"
  log "Done. Restart your shell (or 'source $rc'), then run: aroxy --help"
}

reset_existing() {
  local current="$1"
  [[ -n "$current" ]] || return 0
  log "aroxy is already installed (current version: $current)."
  stop_service
  rm -rf "$AROXY_HOME"
  log "Removed the previous installation (state directory $AROXY_HOME) for a clean reinstall."
}

announce() {
  local current="$1" new="$2"
  if [[ -n "$current" ]]; then
    log "Reinstalling it from scratch (new version: $new)."
  else
    log "Installing aroxy (version: $new)."
  fi
}

main() {
  local current=""
  [[ -f "$INSTALLED_JAR" ]] && current="$(jar_version "$INSTALLED_JAR")"
  reset_existing "$current"
  mkdir -p "$AROXY_HOME"
  chmod 700 "$AROXY_HOME"
  acquire_jar "$STAGED_JAR"
  announce "$current" "$(jar_version "$STAGED_JAR")"
  mv -f "$STAGED_JAR" "$INSTALLED_JAR"
  log "Installed jar: $INSTALLED_JAR"
  write_launcher
  write_completion
  setup_shell
}

main "$@"
