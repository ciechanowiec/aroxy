# CLAUDE.md - aroxy

`aroxy` ("Agent Proxy") is a local proxy between a coding agent and the API it talks to. It
obfuscates secrets and configured keywords in each request before egress and deobfuscates the
response before it reaches the agent, so the client sees real values while the network and the vendor
see only tokens like `<<PII:AWS_KEY:1>>`.

Two agents are served, each on its own port, by one daemon sharing one vault, one rule set, and one
audit log: **Claude Code** on `8788` to `api.anthropic.com`, and **Codex CLI signed in with ChatGPT**
on `8789` to `chatgpt.com/backend-api/codex`. A port per agent means no request is routed by guessing
which vendor a path belongs to.

**Boundary:** this is data minimization, not circumvention. It removes sensitive content from what
leaves the machine; it does not hide that an agent is used or change the destination.

## Build & test

- **Verification command (the full harness): `mvn clean package -Pfull`.** One command runs every
  check: compile, Error Prone + NullAway, Checkstyle + PMD + SpotBugs, per-class JaCoCo (>= 80%
  instruction *and* branch, each class on its own), unused-dependency analysis, the EditorConfig check,
  the dependency-license allowlist, the executable uber-jar, plus the slow and history-wide gates: the
  README documentation lint (the three-engine `.docs/lib/adoc_lint.py` in Docker), the gitleaks secret
  scan over the whole history (needs Docker), the
  whole-history commit-message and typography checks, the dependency-freshness check, PIT mutation
  testing, and the Qodana inspection scan (IntelliJ inspections in Docker). This MUST stay green; the
  head of `main` passes it.
- **Fast partial: `mvn clean package`** runs every default-build gate but skips the slow/history ones
  (the `full`-tagged tests, gitleaks, freshness). No Docker needed; this is what `install.sh` and
  `release.yaml` build.
- **Fastest inner loop: `mvn -q clean test`** (compile + the fast test subset; static analysis and
  coverage run at `package`).
- **No build that verifies writes to your sources.** The three source-shaping tools run as their
  check-only goals, so a pending rewrite, an unformatted file, or an unsorted import fails the build
  rather than being applied behind your back. **`mvn process-resources -Pformat` applies all three**;
  add `-Pformat` to any longer build to apply and carry on. The pom explains why the split is
  opt-in-to-write.
- **Every gate binds at or below `package`; nothing binds to `verify`.** The `-Pfull` profile only adds
  more executions at the `package` phase, so `package` is still the top of the build.
- `-Pfull` needs Docker on `PATH` for the documentation lint, gitleaks, and Qodana; a precheck
  (`scripts/require-docker.sh`) fails early when Docker is absent. Nothing needs a local Vale,
  Asciidoctor, or Python install.
- CI (`.github/workflows/verify.yml`) reruns `mvn clean package -Pfull` on every push to `main` and
  every pull request, over the full history. Run `dev-setup.sh` once to enable the local git hooks in
  `.githooks/` (commit-message validation, a fast pre-commit, and `-Pfull` on pre-push).
- Run the CLI: `java -jar target/aroxy.jar --help`.
- Requires JDK 25 (Temurin), Maven 3.9+, and (for `-Pfull`) Docker.

## Conventions (match these; the build enforces most of them)

- **Java 25**, `groupId eu.ciechanowiec`, `artifactId aroxy`, package `eu.ciechanowiec.aroxy`.
- **Logging: tinylog** via SLF4J (`@Slf4j`). Not logback. Config in `src/main/resources/tinylog.properties`.
- **Lombok** for boilerplate. `@Builder` and `@PostConstruct` are **banned** by Checkstyle.
- **Tests: JUnit 5, NO mocks** (no Mockito). Use real objects, real in-JVM servers
  (`com.sun.net.httpserver.HttpServer`), real temp files. JSON validity is asserted with real
  `org.json` (test scope). A `*Test` class declares no `throws`: use Lombok `@SneakyThrows` on the
  method instead (enforced by the custom PMD rule `NoThrowsInTestClass`).
- **Javadoc names a type by linking it.** `{@link X}` wherever the name resolves from the file, on every
  mention; `{@code X}` only for what a link cannot reach.
- **Static analysis is strict and fails the build.** The configs under
  `src/main/resources/static_code_analysis/` are the enumeration and the source of truth; listed here are
  only the rules that shape how code is written rather than merely tidy it, so do not grow this list
  rule by rule: no `== null` / `!= null` / `return null` (use `Optional`); `this.`-qualify members;
  cognitive complexity ≤ 4; cyclomatic ≤ 7; ≤ 4 params per method/ctor; ≤ 7 fields per class; no nested
  if/for/try; ≤ 2 returns (≤ 1 in a void method); no
  `catch (Exception)`, and a caught exception is named `_` when unused or `exception` when used; names
  spell their words out (`directory`, not `dir`, and `logsDirectory` as surely as `directory`); braces on
  every control statement; no magic numbers; a literal repeated > 6 times must be a constant; method ≤ 50
  lines; file ≤ 500 lines; line ≤ 120 (Java only); no useless `@SuppressWarnings`; `System.getenv` only in
  `daemon/Home`; no call into a deprecated API. The PMD `reportLevel` for the two complexity metrics reads
  one higher than the cap, because PMD reports a method that reaches the level, not one that passes it.
- **Dependency versions** are pinned via properties; the enforcer requires Java 25 and upper-bound
  dependency convergence, bans Mockito and logback, and `versions-maven-plugin` reports available
  updates at `package`.

## Governance harness (confidence without reading)

Nearly every quality expectation here is a machine-enforced check with two verdicts, all reachable from
the one verification command above. Where a rule cannot be machine-checked it says so, rather than
implying a gate that does not exist. Beyond static analysis and coverage, the harness adds:

- **Custom governance checks** live as test-scope Java in
  `src/test/java/eu/ciechanowiec/aroxy/governance/`: pure `*Rules` classes with unit tests, plus
  drivers that read real `git` and filesystem state (no mocks). They cover commit messages, typography,
  dependency freshness, instruction-file references, and Javadoc links, and are held to the same static
  analysis as production code.
- **What a file-scanning check reads** is every file a commit from the working tree would carry:
  what git tracks, plus what is present and not ignored (`Repository.trackedFiles`). Scanning only
  what git already tracks would pass over a whole feature's worth of new sources and report green on
  a tree it had not read, then fail on the next run once those files were committed.
- **Typography.** The em dash, en dash, single-character ellipsis, and curly quotation marks are banned
  from every tracked file and every commit message; use the plain ASCII hyphen, three periods, and
  straight quotes. Vendored roles are exempt: `.vale/`, `.idea/`, `.docs/themes/`, and binaries.
- **Comment prose.** No comment in a tracked Java source joins two clauses with a semicolon: a comment
  is read under time pressure, so the sentence that survives is the short one, and there is always a
  shorter reading (a full stop where both clauses stand alone, a comma where the second is
  subordinate). A `@return` is the exception in both directions, being a fragment completing
  "returns ...": it carries **no** full stop, and with none available a semicolon is what joins a
  second clause to it. Every comment form counts (Javadoc, block, and line), in main and test sources
  alike, and so does a `@Justification` value, which is a comment that happens to be written as an
  annotation. Exempting it would let a suppression's reason say what a comment beside it may not. Every
  other string literal is tokenized out, so a `"https://..."` is never read as a comment. Code
  inside `<pre>`, an inline `{@code}`/`{@literal}`/`{@link}`, and HTML entities are skipped, because a
  semicolon in them is syntax. The em and en dash are **not** this rule's concern. `TypographyRules`
  owns them for every file, and one guideline gets one owner.
- **Prose in the Markdown and AsciiDoc documents follows the same guideline, but binds by inspection.**
  No gate reads them, because the two uses a gate would have to tell apart are not distinguishable by
  pattern. A semicolon *joining two clauses* has a shorter reading and is what the guideline is about.
  A semicolon *separating items in a series whose items already contain commas* has no shorter reading:
  the static-analysis enumeration under Conventions above is one list of some twenty items, and commas
  there would be ambiguous while twenty sentences would be unreadable. Those stay. So does a semicolon
  inside a labelled counter-example, of which `README-guideline-writing.adoc` has one on purpose. Both
  are the prose equivalent of the `<pre>` and `{@code}` regions the Java scan already skips, and a rule
  that flagged them would be teaching the wrong lesson rather than enforcing this one.
- **Secrets.** `gitleaks` scans the whole history for credentials under `-Pfull` (via Docker). Fake
  test fixtures are allowlisted in `.gitleaks.toml`; a real secret must never reach a commit.
- **Dependency licenses.** Every resolved dependency must carry a license on the allowlist (Apache-2.0,
  MIT, MIT-0, BSD-2-Clause, BSD-3-Clause, EPL-2.0, LGPL-2.1, JSON, Public Domain), enforced by
  `license-maven-plugin`.
- **Dependency freshness.** A declared dependency that trails its latest release by >= 2 major versions
  fails the build (fail-closed when the registry is unreachable). Date-versioned releases (e.g.
  `org.json`) have no comparable major and are outside this check.
- **EditorConfig.** The committed `.editorconfig` is enforced on every tracked file (binaries and
  vendored roles excluded). Its whitespace and encoding keys are gated; `max_line_length` is not, and
  for Java the Checkstyle `LineLength` rule holds that limit instead.
- **Unused dependencies.** An unused declared, or used but undeclared, dependency fails the build.
- **No self-editing build.** OpenRewrite, the formatter, and the import sorter each run as their
  check-only goal, so a green build is an honest statement about the committed tree rather than about a
  tree the build reshaped on its way past. Behind them, `scripts/tree-snapshot.sh` fingerprints the tree
  at `validate` and compares at `package` (and again after the Docker-backed gates under `-Pfull`),
  catching any writer the three do not cover. Apply with `-Pformat`, which unbinds both comparisons.
- **Mutation testing.** PIT mutates the pure-logic packages (`obfuscation`, `config`) under `-Pfull`;
  every killable mutant is killed, and the handful of proven equivalent mutants are enumerated in the
  pom and bounded by `maxSurviving`, so a new surviving mutant fails the build.
- **Qodana (IntelliJ inspections).** Under `-Pfull`, `scripts/qodana.sh` runs the Qodana engine over
  `qodana/profile.xml` and fails on any finding (`qodana.yaml` sets `failThreshold: 0`). The profile is
  a curated IntelliJ inspection profile, reusable across repositories; the only inspections disabled in
  it are documented at its top (a few broadly-inapplicable ones and mutually-contradictory pairs, e.g.
  `WeakerAccess` vs `ClassEscapesItsScope`). A finding that is a genuine project-specific false positive
  is suppressed locally, not by weakening the profile.
- **Suppressions.** A suppression is never useless: PMD's `UnnecessaryWarningSuppression` fails the
  build on one that suppresses nothing. It is never unexplained either: every `@SuppressWarnings` is
  paired with `@Justification("...")` saying why the rule does not apply there, enforced by the custom
  PMD rule `SuppressionNeedsJustification`. The annotation lives in `eu.ciechanowiec.governance`, has
  `SOURCE` retention, and is filtered out of the executable jar, so it costs the artifact nothing. It
  sits outside the project's own package so the harness stays copyable. That a suppression never spans a
  whole file still binds by inspection rather than by a gate. A static-analysis suppression names a rule
  from the configs under `src/main/resources/static_code_analysis/`. A Qodana suppression names the
  inspection and lives at the finding (`@SuppressWarnings("<inspection suppress id>")`, or a
  `//noinspection <Id>` line where the annotation form does not apply - that form carries its reason as
  an adjacent comment, since no annotation attaches to it). Prefer decomposition or a fix over a
  suppression.

## Reusing this harness

The harness is written to be lifted into other Java projects, AEM and Sling among them, so most of it
carries no trace of aroxy and is copied as it stands: the `scripts/`, the `.githooks/`, the
`.github/workflows/`, `.docs/lib/adoc_lint.py` with `.vale/` and `.vale.ini`, `qodana/profile.xml` and
`qodana.yaml`, `.editorconfig`, `.gitleaks.toml`, `rewrite.yml`, the configs under
`src/main/resources/static_code_analysis/`, the `governance` test package, and the
`eu.ciechanowiec.governance` annotation in main sources. Rules that no aroxy class
can trip are kept on purpose, because a project that inherits the harness will trip them: the Sling
injection and servlet rules are there for the AEM projects, not for this one.

What a new project changes:
- **Coordinates** - `groupId`, `artifactId`, `main.class`, `finalName`, and the package the analysis
  configs name (`System.getenv` is confined to `daemon/Home` here; that is an aroxy rule).
- **PIT targets** - `targetClasses`/`targetTests` name aroxy's pure-logic packages, and the enumerated
  equivalent mutants with `pitest.max.surviving` describe aroxy's code. Both are re-derived per project;
  a copied `maxSurviving` is meaningless.
- **JaCoCo excludes** - the per-class exclusions name aroxy classes, by the role argued next to them.
- **Banned dependencies** - the enforcer bans Mockito and logback because aroxy's conventions do. A
  project that mocks or uses logback drops that rule; it is a choice, not part of the harness.
- **The documented target** - `scripts/lint-docs.sh` is pointed at `README.adoc` by the pom, and
  `InstructionReferenceTest` reads `CLAUDE.md`.
- **Docker image pins** - `gitleaks.image` and `qodana.image` are properties; keep them pinned.

## Commit messages and history

- **Format (checked over the whole history).** Every header is `type(scope): subject` with `type` from
  the closed list `feat, fix, docs, refactor, perf, test, build, ci, chore, revert`; the scope is
  optional. The subject is 15 to 72 characters, ends without a period, and contains no junk word
  (`wip, tmp, temp, misc, stuff, asdf, fixup`), and a `!` may mark a breaking change. A change over the
  triviality threshold (more than 2 files or more than 50 changed lines) carries a body that says why.
  Merge commits (`Merge ...`) and revert commits (`Revert "..."`) keep their fixed git forms, so they are
  exempt from the header shape alone.
- **No AI-agent attribution.** No commit message names an automated agent or session: no
  `Co-authored-by` an agent, no "Generated with/by" an agent, no agent session id, no agent product
  URL. The check scans every commit, including the ones exempt from the header shape; because no pattern
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
                                                └─ reload watcher (polls control files)
                                        one Obfuscator + one Vault + one AuditLog, shared by both
```

The CLI drives the daemon with no control socket: `status`/`config`/`list` read the daemon's
published `runtime` snapshot, `reload`/`attach` touch a `reload` trigger the daemon polls, and
`stop` sends a process signal that the daemon's shutdown hook turns into a clean teardown. This
removes the network-reachable control plane entirely.

One shared daemon serves every agent session on the machine: Claude Code points
`ANTHROPIC_BASE_URL` at `127.0.0.1:8788`, Codex points a `~/.codex/config.toml` provider `base_url`
(with `requires_openai_auth = true`, so ChatGPT sign-in is kept) at `127.0.0.1:8789`. They share one
proxy, one vault, and one audit log, so a secret carries the same token whichever agent sends it.
Shell completion is set up by `install.sh` (a managed block in the user's rc); the
`generate-completion` subcommand it calls is hidden from `--help`. `uninstall.sh` reverses install.

**Adding or removing a client is additive.** `client/ClientRegistry` is the only place that enumerates
agents; everything else iterates it (listeners bound, `[gateway]` listen keys accepted and validated,
`aroxy status`/`config` lines rendered, `runtime` records published). `ClientId` is an open, validated
slug value rather than an enum, so a new agent is one `ClientProfile` (an id and display name, a
`Gateway`, a `PathScope`, and an `SseDialect`) plus one line in the registry.

Packages under `eu.ciechanowiec.aroxy`:
- `client` - the extension point: `ClientId`, `ClientProfile`, `ClientRegistry`, `Gateway`,
  `PathScope`, and the stream dialect as *data* (`SseDialect` composing `FragmentRule` + `SseKind`),
  with one profile class per agent (`ClaudeCode`, `CodexCli`). No vendor vocabulary lives anywhere else.
- `obfuscation` - the engine: `Obfuscator`, `Vault`, `Rule` (composes `RulePattern` + `RuleLabel`),
  `RuleApplication`, `PiiToken`, `MaskedSecret`, `RuleCompiler` (loose/leet),
  `StreamDeobfuscator` (SSE carry-buffer over text fragments), `ObfuscationResult`/`ObfuscationMatch`.
- `config` - `ConfigFile` parser, `EffectiveConfig` merge, `AttachmentRegistry`, `Settings`
  (a `Gateway` per registered client, keyed by `ClientId`).
- `proxy` - `ProxyServer` (one per client), `Upstream` (plain `HttpClient`, default proxy/trust),
  request handling, `ZstdBody` request inflation, `WebSocketUpgrade` refusal, `SseTransformer`
  (interprets a dialect; knows no vendor's event names), `AuditLog`, wire dumps.
- `daemon` - background launcher, PID file, the file/signal control channel (`ControlFiles`,
  `RuntimeSnapshot`/`SnapshotPublisher`, `ReloadWatcher`), `GatewayAddress`/`GatewayListing`,
  `Home` path layout.
- `cli` - picocli root `Aroxy`, its subcommands, and shell autocompletion (`generate-completion`).

State dir `~/.aroxy/`: `aroxy.pid`, `runtime` (the daemon's published snapshot), `reload` (the
reload trigger), `temporaries` (this session's temporary attachments, cleared on start),
`default.conf` (materialized, user-editable), `attachments` (persistent registry), `vault.tsv`,
`logs/{aroxy.log,audit.log,wire/}`.

## Engine behaviors that must not regress

- Structured request safety: JSON agent requests are parsed before matching, rules see decoded string
  values, and protocol fields declared opaque by the client profile are never transformed.
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
- **Encoded in-scope request bodies fail closed.** Codex can zstd-compress under ChatGPT auth; a valid
  frame is inflated before the rules run and its copy drops `Content-Encoding`, which
  `RestrictedHeaders` deliberately forwards. A malformed zstd frame or any other unsupported encoding
  receives a local HTTP 415 and never reaches the upstream; an opaque body must not egress while the
  proxy reports itself healthy (`ZstdBody`).
- Stable vault, secret masking in logs.
- Sensitivity-aware persistence: `sensitive=true` values stay in memory only and NEVER reach
  `vault.tsv`; only non-sensitive pairs are persisted (so their tokens survive a restart). The split
  is hard-coded, not configurable.

## Configuration model

Effective config = **default.conf** (always, bundled → materialized, user may empty) →
**persistent** attachments (machine/company-wide, every start) → **temporary** attachments (this
session only). Settings: last file wins per key. Rules: keyed by `id`, a later file replaces an
`id` in place; new ids append; order breaks equal-span match ties. Attach allowed while running;
**detach only while stopped**.

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
`ciechanowiec/aroxy`; `install.sh` pulls that jar or builds from source. Not published to Maven Central.
