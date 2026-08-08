<!-- BEGIN AIRNESS MANAGED INSTRUCTIONS -->
## Airness harness

Follow the repository contract from general to specific:

1. Follow `README-guideline-software-project.adoc`.
2. Follow the complete Airness contract in `.airness/agent-guide.md`.
3. Follow the project-owned instructions below this managed block.

Every layer is binding, and a later layer may strengthen but never weaken an earlier one. The inherited harness is
the executable enforcement of this contract. Do not weaken, replace, duplicate, or version what Airness owns. Use
only the write commands declared by the guide, review their edits, and complete its required verification before
finishing.
<!-- END AIRNESS MANAGED INSTRUCTIONS -->

# AGENTS.md - aroxy

This is the root instruction file for Aroxy-owned rules. The Airness-managed block above imports the
shared contract before these project rules. The file is named for no particular agent tool, so each
tool's own entry file (`CLAUDE.md`) holds nothing but a reference to this one.

`aroxy` ("Agent Proxy") is a local proxy between a coding agent and the API it talks to. It
obfuscates secrets and configured keywords in each request before egress and deobfuscates the
response before it reaches the agent, so the client sees real values while the network and the vendor
see only tokens like `<<PII:AWS_KEY:1>>`.

Three agents are served, each on its own port, by one daemon sharing one vault, one rule set, and one
audit log: **Claude Code** on `8788` to `api.anthropic.com`, **Codex CLI signed in with ChatGPT** on
`8789` to `chatgpt.com/backend-api/codex`, and **Cursor CLI** (`agent`) on `8790` to
`api2.cursor.sh`. A port per agent means no request is routed by guessing which vendor a path belongs
to.

**Boundary:** this is data minimization, not circumvention. It removes sensitive content from what
leaves the machine. It does not hide that an agent is used or change the destination.

## Build & test

- The project inherits the shared Java harness from
  `eu.ciechanowiec:airness-parent:1.0.0-SNAPSHOT`. Airness owns analyzer versions, rule sets,
  source shaping, coverage, repository governance, and the files its asset manifest marks pinned.
  Do not recreate a local copy of an inherited rule or plugin configuration.
- Until the SNAPSHOT is published, first run `mvn clean install` in the Airness checkout. A clean
  machine, including the current GitHub runners, cannot resolve this parent from a remote repository.
- **Extended verification: `mvn clean package -Pextended`.** It runs every default gate plus commit-history and
  typography checks, dependency freshness, mutation testing, gitleaks, Qodana, and the project-owned
  README lint. Gitleaks and Qodana require Docker. The README lint requires the local documentation
  toolchain described by `README.adoc`.
- **Default verification: `mvn clean package`.** It compiles, tests, runs Error Prone and NullAway,
  Checkstyle, PMD, SpotBugs, per-class JaCoCo, dependency and license checks, repository governance,
  and builds the executable jar without the slow profile.
- **Inner loop: `mvn -q clean test`.** Analyzer and coverage gates that bind later still require a
  package build before a change is complete.
- Verification is check-only. `mvn process-resources -Pformat` applies the Airness OpenRewrite,
  formatting, and import-order changes and then checks their result.
- `-Dairness.enforce=false` reports findings without failing on them. `-DskipTests` bypasses tests and
  the Airness verdict when an artifact alone is required.
- Run the CLI with `java -jar target/aroxy.jar --help`.
- **Starting the daemon needs no OS-level job submission.** `aroxy start` already spawns a detached JVM
  (`daemon/Launcher`) and writes under `~/.aroxy/logs/`. Do not wrap it in `launchctl`, systemd,
  `nohup`, or another scheduler. Invoke it normally and inspect the logs to confirm startup.
- Requires JDK 25, Maven 3.9.16 or later, the locally installed Airness SNAPSHOT, and Docker plus the
  documentation tools for extended verification.

## Conventions

- **Java 25**, `groupId eu.ciechanowiec`, `artifactId aroxy`, package `eu.ciechanowiec.aroxy`.
- **Logging:** tinylog via SLF4J (`@Slf4j`), never logback. Configuration is in
  `src/main/resources/tinylog.properties`.
- **Lombok:** use it for boilerplate. `@Builder` and `@PostConstruct` are banned. A type that needs
  non-default accessors declares `@Accessors` locally rather than changing the shared `lombok.config`.
- **Tests:** JUnit 5 with no mocks. Use real objects, in-JVM servers, and temporary files. A `*Test`
  class declares no `throws`; use `@SneakyThrows` on the method where necessary.
- **Javadoc:** link every resolvable type name with `{@link X}`. Use `{@code X}` only when no type link
  can resolve.
- **Static analysis is strict.** The active Airness artifact is the source of truth. Important shaping
  rules include: no null comparisons or null returns; `this.`-qualified members; cognitive complexity
  at most 4; cyclomatic complexity at most 7; at most 4 parameters and 7 fields; no nested control
  flow; at most 2 returns; no `catch (Exception)`; descriptive unabbreviated names; braces everywhere;
  no `final` locals or parameters; no magic numbers; methods at most 50 lines; files at most 500 lines;
  Java lines at most 120 columns; no deprecated APIs; and no useless suppressions.
- Dependency versions local to aroxy are pinned through properties. Airness owns shared dependency and
  plugin versions plus Java and upper-bound convergence enforcement. Aroxy additionally bans Mockito
  and logback.

## Governance harness

- Airness supplies the reusable governance code and Maven goals. Aroxy keeps no governance sources,
  analyzer XML, rewrite recipe, Qodana profile, or harness scripts of its own.
- Aroxy-owned rules occur once in `AGENTS.md`, and `CLAUDE.md` contains only `@AGENTS.md`.
- The em dash, en dash, single-character ellipsis, and curly quotation marks are banned from
  committable files and commit messages. `.vale/`, `.idea/`, and `.docs/themes/` are excluded by their
  vendored roles.
- No Java comment joins clauses with a semicolon. A `@return` fragment has no full stop and may use a
  semicolon to join another fragment. This includes `@Justification` values. Prose documents follow the
  same guideline by inspection where syntax and complex lists require judgment.
- `.airness/agent-guide.md`, `.editorconfig`, `.gitattributes`, `.java-version`, `CLAUDE.md`, `lombok.config`,
  `qodana.yaml`, and `README-guideline-software-project.adoc` are pinned Airness assets. `.gitignore` and
  `.gitleaks.toml` were seeded and are project-owned. `rewrite.yml` and `qodana/profile.xml` are forbidden because
  Airness supplies them from artifacts.
- Gitleaks scans the complete history. Only fake fixtures may be allowlisted in `.gitleaks.toml`; a
  real secret must never reach a commit.
- Every resolved dependency needs an allowed license, declared dependencies must be used, used
  dependencies must be declared, and a dependency lagging by at least two comparable major versions
  fails extended verification.
- Verification snapshots every committable file, fails without rewriting a changed pinned asset, and
  fails if a later build step changes that tree. `airness:assets-sync` is the explicit asset repair
  command, while the `format` profile is the explicit Java source-writing path.
- PIT targets `eu.ciechanowiec.aroxy` except the role-based process boundaries configured in `pom.xml`.
  `mutation-baseline.tsv` is project-owned and records each accepted survivor with its reason.
- Qodana runs the profile embedded in Airness against a read-only repository copy. A genuine
  project-specific false positive is suppressed locally, never by weakening the shared profile.
- Every `@SuppressWarnings` has a sibling `@Justification("...")` imported from
  `eu.ciechanowiec.airness`. Prefer decomposition or a fix over suppression. A Qodana-only suppression
  names the inspection at the finding and explains a `//noinspection` form in an adjacent comment.

## Commit messages and history

- **Format (checked over the whole history).** Every header is `type(scope): subject` with `type` from
  the closed list `feat, fix, docs, refactor, perf, test, build, ci, chore, revert`. The scope is
  optional. The subject is 15 to 72 characters, ends without a period, and contains no junk word
  (`wip, tmp, temp, misc, stuff, asdf, fixup`), and a `!` may mark a breaking change. A change over the
  triviality threshold (more than 2 files or more than 50 changed lines) carries a body that says why.
  Merge commits (`Merge ...`) and revert commits (`Revert "..."`) keep their fixed git forms, so they are
  exempt from the header shape alone.
- **No AI-agent attribution.** No commit message names an automated agent or session: no
  `Co-authored-by` an agent, no "Generated with/by" an agent, no agent session id, no agent product
  URL. The check scans every commit, including the ones exempt from the header shape. Because no pattern
  catches every agent, this rule also binds in prose and by inspection.
- **History is append-only.** A published commit is corrected by a new commit, never by rewriting. No
  gate can see this (a rewrite is invisible to a checkout of the rewritten history), so it binds by
  inspection.

## Architecture

```
aroxy CLI (picocli) ──state files + signal──> aroxy daemon (background JVM)
  start/stop/restart/status/config/reload/      ├─ listener 127.0.0.1:8788 ── Claude Code
  logs/config attach|detach|list                │     obfuscate ⇄ deobfuscate ── api.anthropic.com
                                                ├─ listener 127.0.0.1:8789 ── Codex CLI
                                                │     obfuscate ⇄ deobfuscate ── chatgpt.com/backend-api/codex
                                                ├─ listener 127.0.0.1:8790 ── Cursor CLI (HTTP/1.1 + HTTP/2)
                                                │     Run / RunSSE / BidiAppend ⇄ api2.cursor.sh
                                                └─ reload watcher (polls control files)
                                        one Obfuscator + one Vault + one AuditLog, shared by all
```

The CLI drives the daemon with no control socket: `status`/`config`/`list` read the daemon's
published `runtime` snapshot, `reload`/`attach` touch a `reload` trigger the daemon polls, and
`stop` sends a process signal that the daemon's shutdown hook turns into a clean teardown. This
removes the network-reachable control plane entirely.

Cursor's agent stream was observed negotiating `application/connect+proto` over cleartext HTTP/2,
not the JSON dialect, and chunking its visible text at roughly eleven characters - shorter than a
token. That is why `CursorTransport` carries protobuf field-number paths for the streamed values and
why the response path holds a partial token across frames: without it every token in a reply reaches
the terminal unreversed. The JSON dialect is still accepted and still reassembles, but it is the path
Cursor does not take.

One shared daemon serves every agent session on the machine: Claude Code points
`ANTHROPIC_BASE_URL` at `127.0.0.1:8788`, Codex points the built-in provider's `openai_base_url` in
`~/.codex/config.toml` at `127.0.0.1:8789`, and Cursor CLI
points `--endpoint` or `CURSOR_API_ENDPOINT` at `http://localhost:8790`. Cursor's listener accepts its
native cleartext HTTP/2 stream and its HTTP/1.1 compatibility transport. They share one proxy, one
vault, and one audit log, so a secret carries the same token whichever agent sends it.
Shell completion is set up by `install.sh` (a managed block in the user's rc). The
`generate-completion` subcommand it calls is hidden from `--help`. `uninstall.sh` reverses install.

**Adding or removing a client is additive as far as the registry goes, and only that far.**
`client/ClientRegistry` is the only place that enumerates agents, and everything else iterates it
(listeners bound, `[gateway]` listen keys accepted and validated, `aroxy status`/`config` lines
rendered, `runtime` records published). `ClientId` is an open, validated slug value rather than an
enum, so nothing has to be taught that another agent exists.

How far that reaches depends on how the new agent talks, and there are two cases rather than one.
An agent whose conversation is one of the shapes already carried is genuinely one `ClientProfile` (an
id and display name, a `Gateway`, a `PathScope`, and a `ClientTransport`) plus one line in the
registry: Codex was added that way, being a `BufferedTransport` like Claude Code with a different
`SseDialect`. An agent needing a conversation shape aroxy does not yet have is a larger change, and
Cursor is the worked example: `ClientTransport` is **sealed**, so a third variant means permitting it
there, adding an arm to the exhaustive `switch` in `ProxyServer`, and writing the listener that arm
returns. The seal is what makes that visible. It is why the compiler names every place that must
change rather than letting a new transport reach a listener that silently cannot serve it, and it is
the reason the cost lands at compile time instead of at the first request.

Packages under `eu.ciechanowiec.aroxy`:
- `client` - the extension point: `ClientId`, `ClientProfile`, `ClientRegistry`, `Gateway`,
  `PathScope`, and the transport as *data* (buffered HTTP with an `SseDialect`, or Cursor protobuf),
  with one profile class per agent. No vendor vocabulary lives outside the corresponding profile or
  transport implementation.
- `obfuscation` - the engine: `Obfuscator`, `Vault`, `Rule` (composes `RulePattern` + `RuleLabel`),
  `RuleApplication`, `PiiToken`, `MaskedSecret`, `RuleCompiler` (loose/leet),
  `StreamDeobfuscator` (SSE carry-buffer over text fragments), `ObfuscationResult`/`ObfuscationMatch`.
- `config` - `ConfigFile` parser, `EffectiveConfig` merge, `AttachmentRegistry`, `Settings`
  (a `Gateway` per registered client, keyed by `ClientId`).
- `proxy` - `ProxyServer` (one per client) picking a `ProxyListener` off the sealed transport,
  request handling, `ZstdBody` request inflation, `WebSocketUpgrade` refusal, `SseTransformer`
  (interprets a dialect, knowing no vendor's event names), Cursor's protobuf (`ProtobufWire`,
  `ProtobufTransformer`, `ProtobufLeaf`) and Connect exchange (`ConnectJsonFrame`,
  `CursorConnectStream`, `CursorDeltaStream`, `CursorResponseStream`), `AuditLog`, wire dumps.
- `daemon` - background launcher, PID file, the file/signal control channel (`ControlFiles`,
  `RuntimeSnapshot`/`SnapshotPublisher`, `ReloadWatcher`), `GatewayAddress`/`GatewayListing`,
  `Home` path layout.
- `cli` - picocli root `Aroxy`, its subcommands, and shell autocompletion (`generate-completion`).

**Two HTTP stacks, on purpose.** `JdkProxyListener` (the JDK `HttpServer`) serves the buffered clients
and `Upstream` (the JDK `HttpClient`) forwards them. Cursor needs more than either can do: its native
`Run` is cleartext HTTP/2, and it keeps the request open *while* the response streams, which the JDK
client cannot express (it withholds response headers until the request publisher completes). So Cursor
alone runs on **Jetty** - `CursorProxyListener` (HTTP/1.1 + h2c on one port) and `CursorDuplexUpstream`
(async request content + streaming response listener). Cursor's *buffered* routes still use `Upstream`,
so the plain case stays on the plain client.

Carrying two HTTP clients means their reach is made to agree deliberately, because their defaults do
not. Trust already agrees (Jetty with no key store of its own builds from the platform default trust
managers). The proxy does not: Jetty starts with an empty proxy configuration and would go direct where
the JDK client honours `https.proxyHost`. `JdkProxySelection` reads the same `ProxySelector` and hands
Jetty the answer, so a machine behind an egress proxy reaches all three upstreams or none - never two of
three, failing at connect time with nothing naming the cause.

State dir `~/.aroxy/`: `aroxy.pid`, `runtime` (the daemon's published snapshot), `reload` (the
reload trigger), `temporaries` (this session's temporary attachments, cleared on start),
`default.conf` (materialized, user-editable), `attachments` (persistent registry), `vault.tsv`,
`.daemon.jar` (the snapshot a running daemon executes, staged at launch so replacing the installed
jar cannot disturb a live process), `logs/{aroxy.log,audit.log,wire/}`.

## Engine behaviors that must not regress

- Structured request safety: JSON agent requests are parsed before matching, rules see decoded string
  values, and protocol fields declared opaque by the client profile are never transformed.
- **An encoded field is opaque, and opaque under its own parent.** An attached image or document is
  base64 under `source.data` for Claude Code. For Codex, an attached image is a base64 data URL under
  `content.image_url`, while a replayed tool image uses `output.image_url`. A thinking block's
  signature is under `content.signature`, redacted thinking is under
  `content.data`, and Codex reasoning ciphertext is under `encrypted_content`. None is text anyone
  wrote, so no rule can find a secret in one, while a substitution inside one destroys a value the
  vendor decodes, validates, or dereferences before it reads anything else. This is not a rare case
  but what every screenshot costs: a base64 run is long enough that a short configured value occurs
  in it by chance, and the giveaway in the audit log is a match in a mixed case no author would
  type. The names are parent-qualified where the bare one is shared with content the rules must
  still reach, a tool parameter named `data` being the case that occurs in practice, and left bare
  where the protocol owns the name outright. `RequestObfuscationTest` pins both directions for both
  agents.
- Overlap resolution: all rules inspect the original semantic text. Matches resolve leftmost-longest,
  with configuration order breaking an exact tie, so a shorter literal cannot shadow a longer one.
- Canonical keying: a literal rule's spelling variants share ONE token and restore to the configured value.
- SSE: a token split across two streamed fragments reverses correctly (`StreamDeobfuscator`), in
  either dialect. A partial still held at a terminal event is flushed by *replaying the last fragment
  block* with its value substituted, never by authoring a synthetic event: an authored event would
  have to invent the bookkeeping fields a vendor's parser requires.
- **Whole-block reversal (`SseKind.WHOLE`).** Where a stream repeats its text in a later authoritative
  block, that block must be reversed too. Codex's `response.output_item.done` is the case: it is what
  Codex persists into conversation history and echoes back next turn, so reversing only the deltas
  would leave a token to egress again. Pinned by `CodexStreamTest`.
- **Encoded in-scope request bodies fail closed.** Codex can zstd-compress under ChatGPT auth. A valid
  frame is inflated before the rules run and its copy drops `Content-Encoding`, which
  `RestrictedHeaders` deliberately forwards. A malformed zstd frame or any other unsupported encoding
  receives a local HTTP 415 and never reaches the upstream. An opaque body must not egress while the
  proxy reports itself healthy (`ZstdBody`).
- Stable vault, secret masking in logs.
- Sensitivity-aware persistence: `sensitive=true` values stay in memory only and NEVER reach
  `vault.tsv`. Only non-sensitive pairs are persisted (so their tokens survive a restart). The split
  is hard-coded, not configurable.

## Configuration model

Effective config = **default.conf** (always, bundled → materialized, user may empty) →
**persistent** attachments (machine/company-wide, every start) → **temporary** attachments (this
session only). Settings: last file wins per key. Rules: keyed by `id`, a later file replaces an
`id` in place, new ids append, and order breaks equal-span match ties. Attach is allowed while running.
**Detach only while stopped**.

**A declared key must be usable.** A key states an intent, so a file that declares one its stanza does
not define, or one whose value names nothing the proxy can act on, is broken and is not loaded. An
absent key states no intent and keeps the documented default. This is why `listen_claude = 87788`, an unknown
`[gateway]` key such as `upstream`, `mask_secrets = yes`, and `type = literl` all break the file rather
than falling back: a configuration that reads as applied and is not would leave the user trusting a
proxy that never took the instruction. The `[gateway]` listen keys are *derived* from the client
registry rather than listed in the parser, so a newly registered agent gets its key and that key's
validation for free. Two agents pointed at one port is likewise rejected, at merge time rather than at
bind time, because the clash is a property of the merge (one file may set only one of the keys). Tests
reach ephemeral ports through `DaemonSeam`, not config.

**Placeholders are a closed vocabulary.** The same rule, applied to a rule's `placeholder`: it must be
one of the names shipped in `default.conf` (enforced by `config/Placeholders`). The set is closed
because free-form, code-like names (e.g. `COMPANY_D`) are reformatted by the model in its reply and can
no longer be deobfuscated, whereas the curated names survive the round trip. A test pins the set to
`default.conf` and to the README list.

## Release

The `.github/workflows/release.yaml` workflow builds the jar and publishes it as a GitHub release in
`ciechanowiec/aroxy`. `install.sh` pulls that jar or builds from source. Not published to Maven Central.
