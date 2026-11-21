<!-- BEGIN AIRNESS MANAGED: generic -->
## Airness harness

Follow the repository contract from general to specific:

1. Follow `README-guideline-software-project.adoc`.
2. Follow the complete Airness contract in `.airness/agent-guide.md`.
3. Follow each Airness stack layer below, where it applies.
4. Follow the project-owned instructions below the managed blocks.

Each step is binding, and a later step may strengthen but never weaken an earlier one. The inherited harness is the executable enforcement of this contract. Do not weaken, replace, duplicate, or version what Airness owns.
<!-- END AIRNESS MANAGED: generic -->

<!-- BEGIN AIRNESS MANAGED: java -->
## Airness Java layer

In every module that inherits `airness-parent-java`, also follow `.airness/agent-guide-java.md`.
<!-- END AIRNESS MANAGED: java -->

# AGENTS.md - aroxy

These are the Aroxy-owned rules. Airness refuses every other agent instruction file, so this file is the single entry point for every agent tool.

`aroxy` ("Agent Proxy") is a local proxy between a coding agent and the API it talks to. It obfuscates secrets and configured keywords in each request before egress and deobfuscates the response before it reaches the agent, so the client sees real values while the network and the vendor see only tokens like `<<PII:AWS_KEY:1>>`.

Four agents are served, each on its own port, by one daemon sharing one vault, one rule set, and one audit log: **Claude Code** on `8788`, **Codex CLI signed in with ChatGPT** on `8789`, **Cursor CLI** (`agent`) on `8790`, and **GitHub Copilot CLI** on `8791`. A port per agent means no request is routed by guessing which vendor a path belongs to.

**Boundary:** this is data minimization, not circumvention. It removes sensitive content from what leaves the machine. It does not hide that an agent is used or change the destination.

## Architecture

Read [docs/architecture.md](docs/architecture.md) before changing a listener, a transport, the control channel, the configuration merge, or the release flow. It explains the daemon, the client registry and its sealed transport, the two HTTP stacks, the state directory, the configuration model, and the release invariants. Its design constraints bind as firmly as the rules here, and a change that departs from one updates that document in the same change.

## Build & test

- The project inherits the shared Java harness from `eu.ciechanowiec:airness-parent-java:1.0.13`. Airness owns analyzer versions, rule sets, source shaping, coverage, repository governance, and the files its asset manifests mark pinned. Do not recreate a local copy of an inherited rule or plugin configuration.
- **Extended verification: `mvn clean verify -Pextended`.** It runs every default gate plus the whole-history, typography, secret, Qodana, shell, workflow, and vulnerability checks and the compliance report. Gitleaks and Qodana require Docker, and the compliance report requires the browser that `mvn airness:report-browser-install` installs.
- **Default verification: `mvn clean verify`.** It compiles, tests, runs Error Prone and NullAway, Checkstyle, PMD, SpotBugs, per-class JaCoCo, dependency and license checks, repository governance, and checks the executable jar. The Airness verdict closes in the `verify` phase, so `package` alone is not a verdict, and no project execution may bind to `verify`.
- **Inner loop: `mvn -q clean test`.** Analyzer, coverage, and artifact gates that bind later still require a verify build before a change is complete.
- Verification is check-only. `mvn process-resources -Pformat` applies the Airness formatting and import-order changes and then checks their result.
- `-Dairness.enforce=false` reports findings without failing on them. `-DskipTests` bypasses tests and the Airness verdict when an artifact alone is required.
- Run the CLI with `java -jar target/aroxy.jar --help`.
- **Documentation lint: `adocent lint README.adoc`.** Run it by hand after editing `README.adoc`, as its "About the Document" section describes. The Maven build deliberately doesn't run it.
- **Starting the daemon needs no OS-level job submission.** `aroxy start` already spawns a detached JVM (`daemon/Launcher`) and writes under `~/.aroxy/logs/`. Do not wrap it in `launchctl`, systemd, `nohup`, or another scheduler. Invoke it normally and inspect the logs to confirm startup.
- Requires JDK 25, Maven 3.10.0 or later, network access, and Docker for extended verification.

## Conventions

- **Java 25**, `groupId eu.ciechanowiec`, `artifactId aroxy`, package `eu.ciechanowiec.aroxy`.
- **Logging:** tinylog via SLF4J (`@Slf4j`), never logback. Configuration is in `src/main/resources/tinylog.properties`. A `debug` or `trace` statement whose argument calls a method sits inside a matching level guard.
- **Lombok:** use it for boilerplate. `@Builder` and `@PostConstruct` are banned. A type that needs non-default accessors declares `@Accessors` locally rather than changing the shared `lombok.config`.
- **Nullness:** every production package is `@NullMarked` in its `package-info.java`, so a value that may be absent is declared `@Nullable` or modelled as `Optional`.
- **Tests:** JUnit 5 with no mocks. Use real objects, in-JVM servers, and temporary files. A `*Test` class declares no `throws`; use `@SneakyThrows` on the method where necessary.
- **Javadoc:** link every resolvable type name with `{@link X}`. Use `{@code X}` only when no type link can resolve.
- **Static analysis is strict.** The active Airness artifact is the source of truth. Important shaping rules include: no null comparisons or null returns; `this.`-qualified members; cognitive complexity at most 4; cyclomatic complexity at most 7; at most 4 parameters, 7 fields, and 7 record components; no nested control flow; at most 2 returns; no `catch (Exception)`; descriptive unabbreviated names; braces everywhere; no `final` locals or parameters; no magic numbers; methods at most 50 lines; files at most 500 lines; Java lines at most 120 columns; no deprecated APIs; and no useless suppressions.
- Dependency versions local to aroxy are pinned through properties. Airness owns shared dependency and plugin versions plus Java and upper-bound convergence enforcement. Airness bans mocking libraries as direct dependencies, and aroxy additionally bans Mockito and logback through transitive paths.

## Governance harness

- Airness supplies the reusable governance code and Maven goals. Aroxy keeps no governance sources, analyzer XML, rewrite recipe, Qodana profile, or harness scripts of its own.
- Aroxy-owned rules occur once in `AGENTS.md`. The managed `.claude/settings.json` denies agent edits to every pinned file.
- The em dash, en dash, single-character ellipsis, and curly quotation marks are banned from committable files and commit messages. `.idea/` is excluded because it belongs to the IDE.
- No Java comment joins clauses with a semicolon. A `@return` fragment has no full stop and may use a semicolon to join another fragment. This includes `@Justification` values. Prose documents follow the same guideline by inspection where syntax and complex lists require judgment.
- Pinned Airness assets: `.editorconfig`, `.gitattributes`, `.java-version`, `.mvn/maven.config`, `.mvn/jvm.config`, `.mvn/settings.xml`, `.airness/agent-guide.md`, `.airness/agent-guide-java.md`, `.airness/license-inventory.ftl`, `.airness/test.gitconfig`, `README-guideline-software-project.adoc`, `lombok.config`, and `qodana.yaml`. `AGENTS.md`, `.gitignore`, and `.dockerignore` open with managed blocks, and the project owns only the text below them. `.gitleaks.toml` was seeded and is project-owned. Airness supplies its rewrite recipe and Qodana profile from artifacts, so a committed copy of either fails the build.
- Markdown documents keep each paragraph on one line, fence code with a language, and never repeat a paragraph another document already states. The project part of `AGENTS.md` stays within 2,000 words, which is why design explanations live in `docs/architecture.md`.
- Gitleaks scans the complete history. Only fake fixtures may be allowlisted in `.gitleaks.toml`, each as one anchored exact value; a real secret must never reach a commit.
- Every resolved dependency needs an allowed license, declared dependencies must be used, used dependencies must be declared, and a dependency lagging by at least two comparable major versions fails extended verification.
- Verification snapshots every committable file, fails without rewriting a changed pinned asset, and fails if a later build step changes that tree. `mvn airness:assets-sync` is the explicit asset repair command, while the `format` profile is the explicit Java source-writing path.
- Qodana runs the profile embedded in Airness against a read-only repository copy. A genuine project-specific false positive is suppressed locally, never by weakening the shared profile.
- Every `@SuppressWarnings` has a sibling `@Justification("...")` imported from `eu.ciechanowiec.airness`. Prefer decomposition or a fix over suppression. A Qodana-only suppression names the inspection at the finding and explains a `//noinspection` form in an adjacent comment.
- **Suppressions have a ceiling** of 3 per 1,000 lines of Java source. It counts each suppressed rule, not each annotation, and each entry of `airness.coverage.excluded.classes`, which is written as `pattern -- reason` with entries separated by semicolons. The repository runs close to the ceiling, so a new suppression is paid for by removing an existing one.

## Engine behaviors that must not regress

- Structured request safety: JSON agent requests are parsed before matching, rules see decoded string values, and protocol fields declared opaque by the client profile are never transformed.
- **An encoded field is opaque, and opaque under its own parent.** An attached image or document is base64 under `source.data` for Claude Code. For Codex, an attached image is a base64 data URL under `content.image_url`, while a replayed tool image uses `output.image_url`. A thinking block's signature is under `content.signature`, redacted thinking is under `content.data`, and Codex reasoning ciphertext is under `encrypted_content`. A name is parent-qualified where the bare one is shared with content the rules must still reach, and bare where the protocol owns it. `RequestObfuscationTest` pins both directions for both agents, and the reasoning is in `docs/architecture.md`.
- Overlap resolution: all rules inspect the original semantic text. Matches resolve leftmost-longest, with configuration order breaking an exact tie, so a shorter literal cannot shadow a longer one.
- Canonical keying: a literal rule's spelling variants share ONE token and restore to the configured value.
- SSE: a token split across two streamed fragments reverses correctly (`StreamDeobfuscator`), in either dialect. A partial still held at a terminal event is flushed by *replaying the last fragment block* with its value substituted, never by authoring a synthetic event: an authored event would have to invent the bookkeeping fields a vendor's parser requires.
- **Whole-block reversal (`SseKind.WHOLE`).** Where a stream repeats its text in a later authoritative block, that block must be reversed too. Codex's `response.output_item.done` is the case: it is what Codex persists into conversation history and echoes back next turn, so reversing only the deltas would leave a token to egress again. Pinned by `CodexStreamTest`.
- **Encoded in-scope request bodies fail closed.** Codex can zstd-compress under ChatGPT auth. A valid frame is inflated before the rules run and its copy drops `Content-Encoding`, which `RestrictedHeaders` deliberately forwards. A malformed zstd frame or any other unsupported encoding receives a local HTTP 415 and never reaches the upstream. An opaque body must not egress while the proxy reports itself healthy (`ZstdBody`).
- Stable vault, secret masking in logs.
- Sensitivity-aware persistence: `sensitive=true` values stay in memory only and NEVER reach `vault.tsv`. Only non-sensitive pairs are persisted (so their tokens survive a restart). The split is hard-coded, not configurable.

## Commit messages and history

- **Format (checked over the whole history).** Every header is `type(scope): subject` with `type` from the closed list `feat, fix, docs, refactor, perf, test, build, ci, chore, revert`. The scope is optional. The subject is 15 to 72 characters, ends without a period, and contains no junk word (`wip, tmp, temp, misc, stuff, asdf, fixup`), and a `!` may mark a breaking change. A change over the triviality threshold (more than 2 files or more than 50 changed lines) carries a body that says why. Merge commits (`Merge ...`) and revert commits (`Revert "..."`) keep their fixed git forms, so they are exempt from the header shape alone.
- **No AI-agent attribution.** No commit message names an automated agent or session: no `Co-authored-by` an agent, no "Generated with/by" an agent, no agent session id, no agent product URL. The check scans every commit, including the ones exempt from the header shape. Because no pattern catches every agent, this rule also binds in prose and by inspection.
- **History is append-only.** A published commit is corrected by a new commit, never by rewriting. No gate can see this (a rewrite is invisible to a checkout of the rewritten history), so it binds by inspection.
- **A version change** updates the root `pom.xml` and the `release_version` default in `.github/workflows/release.yaml` together. Search every committable file for the previous version after the edit so a stale pin cannot survive the bump.
