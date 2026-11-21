#!/usr/bin/env bash
#
# aroxy installer (macOS + Linux). Works two ways:
#   * From a source checkout: it uses target/aroxy.jar, building it with Maven if absent.
#   * Standalone (curl -fsSL .../install.sh | bash): it asks Maven Central which version is the
#     newest release and downloads that one. Nothing is pinned here, because this file is served
#     from main, and main carries a SNAPSHOT version between releases -- a pin would name a
#     version Maven Central does not serve.
#
# It always reinstalls from scratch, idempotently and with clear logging:
#   1. Verifies the prerequisites and stops on the first one that is unmet, naming SDKMAN as the
#      suggested source of a JDK. The JDK must be exactly the feature release aroxy is compiled for
#      and is checked before anything on disk is read or written. The tool the chosen path needs
#      (curl to download, Maven to build) is checked before that path starts.
#   2. When aroxy is already installed, it reports the current version and the new version,
#      stops the service if it is running, and removes the previous state directory ~/.aroxy
#      (configuration, vault, logs, and attachments registry) so the reinstall starts clean --
#      the same end state as running uninstall.sh and then install.sh.
#   3. Puts the jar at ~/.aroxy/aroxy.jar.
#   4. Writes a launcher to ~/.local/bin/aroxy and makes it executable.
#   5. Generates a shell completion script to ~/.aroxy/aroxy_completion.
#   6. Adds a reversible managed block to your shell rc (~/.zshrc or ~/.bashrc) that puts
#      the launcher on PATH and enables completion. Re-running replaces the block; uninstall.sh
#      removes it.
#
# Boundary: aroxy is data minimization, not circumvention. It removes sensitive content from
# what leaves the machine; it does not hide that an agent is used nor change the destination.

set -euo pipefail

# Exported so every aroxy JVM this script invokes resolves the same state directory the script
# writes. A JVM on macOS derives user.home from the account database and ignores HOME, so without
# the export a run under an overridden HOME would install into a sandbox while stopping the real
# daemon.
readonly AROXY_HOME="${AROXY_HOME:-${HOME}/.aroxy}"
export AROXY_HOME
readonly BIN_DIR="${HOME}/.local/bin"
readonly LAUNCHER="${BIN_DIR}/aroxy"
readonly INSTALLED_JAR="${AROXY_HOME}/aroxy.jar"
readonly STAGED_JAR="${AROXY_HOME}/.aroxy.jar.new"
readonly COMPLETION="${AROXY_HOME}/aroxy_completion"
readonly PID_FILE="${AROXY_HOME}/aroxy.pid"
readonly RELEASE_BASE_URL="https://repo.maven.apache.org/maven2/eu/ciechanowiec/aroxy"
readonly RELEASE_METADATA_URL="${RELEASE_BASE_URL}/maven-metadata.xml"
readonly BLOCK_START="# >>> aroxy >>>"
readonly BLOCK_END="# <<< aroxy <<<"
readonly STOP_WAIT_ATTEMPTS=50
readonly STOP_WAIT_PAUSE=0.1
# The feature release aroxy's jar is compiled for. A JVM below it cannot load the jar at all, and one
# above it is untested, so either would leave this script installing a launcher that fails on its
# first run. Keep it in step with .java-version and the README prerequisites.
readonly REQUIRED_JAVA=25
readonly JAVA_SUGGESTION="https://sdkman.io"

# Presentation. This mirrors the CLI's own output vocabulary (src/main/java/eu/ciechanowiec/aroxy/cli/
# CliOutput.java) so installing reads like every other aroxy command: a cyan bullet for a step in
# progress, a green check for one that completed, a dim circle for something absent, a yellow bang for
# a warning, a red cross for a failure, and dim padded labels for the values underneath. It is
# duplicated in uninstall.sh rather than sourced from a shared file on purpose, because both scripts
# are used standalone (curl | bash, and copied one by one by the release smoke test), where a sibling
# file would not be there to source. The block is kept identical in both copies so they can be compared
# with a plain diff, which is why it also defines the states this script never reaches.
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

# Prints the directory holding this script, or nothing when it runs from a pipe and has none, so a
# caller tests the answer rather than the status.
script_dir() {
  local src="${BASH_SOURCE[0]:-}" dir
  [[ -n "${src}" ]] || return 0
  dir="$(dirname "${src}")"
  if cd "${dir}" 2>/dev/null; then
    pwd
  fi
}

target_rc() {
  case "${SHELL:-}" in
    */zsh) printf '%s' "${HOME}/.zshrc" ;;
    */bash) printf '%s' "${HOME}/.bashrc" ;;
    *) printf '%s' "${HOME}/.profile" ;;
  esac
}

# Reports what a jar calls itself, or nothing when it cannot say. The failure is swallowed on purpose:
# under `set -e` an assignment inherits the status of the command substitution that feeds it, so an
# unguarded probe of an unreadable jar would abort the script with no message at all.
jar_version() {
  java -jar "$1" --version 2>/dev/null | head -n 1 || true
}

# Reports the first line of `java --version`, or nothing when no usable JVM answers. Guarded for the
# same reason as jar_version.
java_report() {
  java --version 2>/dev/null | head -n 1 || true
}

# Surfaces what java itself complained about, which is the whole message on a machine carrying only a
# stub launcher (macOS ships one that exists on PATH and reports no runtime). Its own words beat any
# guess this script could make. Only stderr is read, and the JVM's option banner is dropped so it
# cannot be mistaken for the diagnosis.
java_diagnosis() {
  java --version 2>&1 >/dev/null | grep -v '^Picked up ' | head -n 1 || true
}

# Extracts the feature version from a `java --version` banner. Vendors disagree on the leading word
# ("openjdk 25.0.3 ...", "java 25.0.1 ..."), so the first version-shaped token is taken rather than a
# fixed field.
java_feature() {
  printf '%s' "$1" | grep -oE '[0-9]+(\.[0-9]+)*' | head -n 1 | cut -d . -f 1 || true
}

java_advice() {
  note "Install JDK ${REQUIRED_JAVA}. The suggested way is SDKMAN: ${JAVA_SUGGESTION}"
}

# Fails closed on an unusable JDK, and does it first, before any state is read or written. Every later
# step already depends on a working java (reading the installed jar's version, stopping a running
# daemon, generating the completion script), and each of those hides java's stderr, so without this
# check a missing JDK either aborts the install with a message blaming the download or, from a
# checkout, completes it and leaves a launcher that dies on first use.
require_java() {
  local report feature java_path diagnosis
  info 'Checking prerequisites'
  if ! command -v java >/dev/null 2>&1; then
    failure "aroxy needs JDK ${REQUIRED_JAVA}, and no java is on PATH"
    java_advice
    exit 1
  fi
  report="$(java_report)"
  if [[ -z "${report}" ]]; then
    java_path="$(command -v java || true)"
    diagnosis="$(java_diagnosis)"
    failure "aroxy needs JDK ${REQUIRED_JAVA}, and the java on PATH does not run"
    detail 'Java' "${java_path}"
    detail 'Reported' "${diagnosis}"
    java_advice
    exit 1
  fi
  feature="$(java_feature "${report}")"
  if [[ "${feature}" != "${REQUIRED_JAVA}" ]]; then
    failure "aroxy needs JDK ${REQUIRED_JAVA}, but PATH offers ${feature:-an unrecognized version}"
    detail 'Java' "${report}"
    java_advice
    exit 1
  fi
  detail 'Java' "${report}"
}

# Fails closed on a missing install tool. Without this the absence surfaces much later as a wrong
# answer rather than a missing program: an unreachable curl leaves the metadata empty, which reads as
# Maven Central publishing no usable release.
require_tool() {
  local tool="$1" purpose="$2"
  command -v "${tool}" >/dev/null 2>&1 && return 0
  failure "aroxy needs ${tool} to ${purpose}, and it is not on PATH"
  exit 1
}

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

# Stops a running daemon and does not return until its process is actually gone, so the caller can
# safely delete the state directory afterwards. The pid is read BEFORE `aroxy stop` (which clears the
# pid file), so the escalation fallback still works if the graceful shutdown fails to exit in time.
stop_service() {
  local pid running="false"
  pid="$(recorded_pid)"
  if [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null; then
    running="true"
    info 'The service is running and will be stopped first'
    detail 'PID' "${pid}"
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
}

acquire_jar() {
  local dest="$1" here version
  here="$(script_dir)"
  if [[ -n "${here}" && -f "${here}/target/aroxy.jar" ]]; then
    info 'Using the locally built jar'
    detail 'Source' "${here}/target/aroxy.jar"
    cp "${here}/target/aroxy.jar" "${dest}"
  elif [[ -n "${here}" && -f "${here}/pom.xml" ]]; then
    require_tool mvn 'build from this checkout'
    info 'Building the jar'
    detail 'Command' 'mvn -q clean package'
    ( cd "${here}" && mvn -q clean package )
    cp "${here}/target/aroxy.jar" "${dest}"
  else
    require_tool curl 'download a release'
    version="$(latest_release_version)"
    info "Downloading aroxy ${version} from Maven Central"
    curl -fsSL "${RELEASE_BASE_URL}/${version}/aroxy-${version}.jar" -o "${dest}"
    verify_jar "${dest}" "${version}"
  fi
}

# Asks Maven Central for the newest published release, reading the <release> element of the
# artifact's maven-metadata.xml, and fails closed when the metadata is unreachable or names no
# usable version. A SNAPSHOT or otherwise malformed answer is rejected rather than turned into a
# download URL, so a corrupted or hostile metadata document cannot steer the install elsewhere.
latest_release_version() {
  local metadata version
  info 'Resolving the newest aroxy release published to Maven Central'
  metadata="$(curl -fsSL "${RELEASE_METADATA_URL}" || true)"
  version="$(printf '%s' "${metadata}" | grep -o '<release>[^<]*</release>' | tail -n 1 |
    sed -e 's|<release>||' -e 's|</release>||' | tr -d '[:space:]' || true)"
  if [[ ! "${version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z]+)*$ || "${version}" == *-SNAPSHOT ]]; then
    failure 'Maven Central published no usable release version'
    detail 'Metadata' "${RELEASE_METADATA_URL}"
    exit 1
  fi
  printf '%s' "${version}"
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
  local jar="$1" version="$2" expected actual reported
  info 'Verifying the download against its published SHA-256'
  expected="$(curl -fsSL "${RELEASE_BASE_URL}/${version}/aroxy-${version}.jar.sha256" | tr -d '[:space:]')"
  if [[ ! "${expected}" =~ ^[0-9a-fA-F]{64}$ ]]; then
    rm -f "${jar}"
    failure 'Maven Central returned no valid SHA-256, so the download was discarded'
    exit 1
  fi
  actual="$(sha256_of "${jar}")"
  if [[ "${actual}" != "${expected}" ]]; then
    rm -f "${jar}"
    failure 'Checksum mismatch, so the download was discarded'
    detail 'Expected' "${expected}"
    detail 'Actual' "${actual}"
    exit 1
  fi
  reported="$(jar_version "${jar}")"
  if [[ "${reported}" != "aroxy ${version}" ]]; then
    rm -f "${jar}"
    failure 'The downloaded jar reports the wrong version, so it was discarded'
    detail 'Expected' "aroxy ${version}"
    detail 'Reported' "${reported:-nothing}"
    exit 1
  fi
  success 'Checksum and version verified'
}

write_launcher() {
  mkdir -p "${BIN_DIR}"
  cat > "${LAUNCHER}" <<EOF
#!/usr/bin/env bash
# AROXY_HOME is exported so the JVM resolves the same state directory this launcher reads the jar
# from. A JVM on macOS derives user.home from the account database and ignores HOME, so without the
# export a launcher run under an overridden HOME would act on the real state directory.
export AROXY_HOME="\${AROXY_HOME:-\$HOME/.aroxy}"
exec java -jar "\$AROXY_HOME/aroxy.jar" "\$@"
EOF
  chmod +x "${LAUNCHER}"
}

write_completion() {
  if java -jar "${INSTALLED_JAR}" generate-completion > "${COMPLETION}" 2>/dev/null; then
    # picocli's generated script only offers flags when the current word starts with '-'. After each
    # else-branch COMPREPLY line (the only ones carrying ${IFS}) append a second compgen for the
    # flags so a bare TAB offers them too. It must run with IFS reset to a space, because the
    # else-branch sets IFS=$'\n' for file positionals while ${flag_opts} is space-separated -- folding
    # them into that newline word list would collapse every flag into one candidate. Appending also
    # fills COMPREPLY on flag-only subcommands like `logs`, suppressing zsh's directory fallback.
    local flag_line
    IFS= read -r flag_line <<'COMPLETION_LINE'
    COMPREPLY+=( $(IFS=" "; compgen -W "${flag_opts} ${arg_opts}" -- "${curr_word}") )
COMPLETION_LINE
    sed -i.aroxy-bak "/\\\${IFS}/ s|\$|\\
${flag_line}|" "${COMPLETION}"
    rm -f "${COMPLETION}.aroxy-bak"
  else
    # The redirection above truncates the file before java runs, so a failure leaves an empty script
    # behind. Removing it keeps the rc block from sourcing nothing and lets the summary report only
    # what was really written.
    rm -f "${COMPLETION}"
    warning 'Could not generate the completion script, so it was skipped'
  fi
}

setup_shell() {
  local rc="$1"
  touch "${rc}"
  if grep -qF "${BLOCK_START}" "${rc}" 2>/dev/null; then
    sed -i.aroxy-bak "/${BLOCK_START}/,/${BLOCK_END}/d" "${rc}"
    rm -f "${rc}.aroxy-bak"
  fi
  cat >> "${rc}" <<'BLOCK'
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
}

# Describes the installation already in place, and reports nothing when there is none. A jar that
# cannot say what it is still counts as installed: the reinstall has to wipe the previous state either
# way, and treating an unreadable jar as absent would leave the old configuration, vault, and shell
# block in place while reporting a clean install.
installed_version() {
  local reported
  [[ -f "${INSTALLED_JAR}" ]] || return 0
  reported="$(jar_version "${INSTALLED_JAR}")"
  printf '%s' "${reported:-an unreadable jar}"
}

reset_existing() {
  local current="$1"
  [[ -n "${current}" ]] || return 0
  info "Already installed: ${current}"
  stop_service
  rm -rf "${AROXY_HOME}"
  success 'Removed the previous installation for a clean reinstall'
  detail 'State' "${AROXY_HOME}"
}

announce() {
  local current="$1" new="$2"
  if [[ -n "${current}" ]]; then
    info "Reinstalling from scratch as ${new}"
  else
    info "Installing ${new}"
  fi
}

# Renders the finished install the way `aroxy start` renders a started daemon: one headline, then the
# paths that now exist. The completion line is conditional because generating it is allowed to fail.
summarize() {
  local version="$1" rc="$2"
  success 'Installed'
  detail 'Version' "${version}"
  detail 'Jar' "${INSTALLED_JAR}"
  detail 'Launcher' "${LAUNCHER}"
  if [[ -s "${COMPLETION}" ]]; then
    detail 'Completion' "${COMPLETION}"
  fi
  detail 'Shell' "${rc}"
  note "Restart your shell (or: source ${rc}), then run: aroxy --help"
}

main() {
  local current="" rc version
  heading 'aroxy installer'
  require_java
  rc="$(target_rc)"
  current="$(installed_version)"
  reset_existing "${current}"
  mkdir -p "${AROXY_HOME}"
  chmod 700 "${AROXY_HOME}"
  acquire_jar "${STAGED_JAR}"
  version="$(jar_version "${STAGED_JAR}")"
  announce "${current}" "${version}"
  mv -f "${STAGED_JAR}" "${INSTALLED_JAR}"
  write_launcher
  write_completion
  setup_shell "${rc}"
  summarize "${version}" "${rc}"
}

main "$@"
