# Airness Agent Guide

This guide maps the governing standard to the Airness harness. After it, continue through the lower `AGENTS.md`
layers in the order the standard declares. Exact analyzer rules remain in the executable harness.

## Layer 1: Governing Standard

- Read `README-guideline-software-project.adoc` before using this Airness-specific mapping.

## Layer 2: Harness Invariants

- The project inherits `airness-parent`. Do not weaken, replace, duplicate, or version the plugins, dependencies,
  analyzers, rules, thresholds, or managed files that the parent owns.
- Use exactly Java 25, Maven 3.9.16 or later, and a Git working tree. Default verification reads Maven Central and
  Docker Hub and fails when either cannot be read, and Extended verification also needs a reachable Docker daemon.
  Keep every production and test package under the `airness.package.root` declared in the root `pom.xml`.
- Treat every finding and every tool, setup, or compilation failure as a failed verification. Reporting findings with
  `-Dairness.enforce=false` is not a pass. A build using `-DskipTests` produces no Airness verdict.

## Layer 3: Governed Domains

Airness governs all of the following domains:

- **Toolchain and Maven model:** runtime versions, project and parent coordinates, package ownership, inherited plugin
  ownership, raw-model anti-bypass checks, effective dependency convergence, the ordering of declared properties, and
  valid Airness parameters.
- **Repository files and instructions:** managed, seeded, and forbidden files; the root license file; agent
  instruction files; editor and Git configuration; and an unchanged committable tree during verification.
- **Source:** formatting, imports, modernization recipes, compilation, nullness, static analysis, documentation
  comments, source comments, typography, banned substitutes, and cycles among the packages of a module.
- **Dependencies:** explicit scopes, exactly named versions, no project-declared repositories or system paths,
  released dependencies for a released project, one version and one owning artifact per class, unused dependencies,
  declared mocking libraries, licenses, known vulnerabilities, available stable package and container-image updates,
  container tag-digest drift, and the maximum permitted coordinate freshness gap.
- **Artifacts:** the finished JAR contains no unsafe or duplicate paths, development or source files, test-only output,
  machine-local repository paths, or recognizable secret material.
- **Tests and evidence:** test execution, test integrity and determinism, a default timeout on every test, a
  shuffled execution order under a declared seed, an assertion in every test, assertions that literals alone cannot
  settle, production-to-test boundaries, per-class line and branch coverage, and current-build coverage evidence.
- **Repository assurance:** secret scanning, Qodana analysis, the ceiling on how many suppressions the repository
  holds, complete Git history, commit-message policy, commit typography, linear history, and history-wide
  compliance.

Do not treat a domain omitted from one command's output as ungoverned. The parent POM, bundled configurations, and
Airness goals together define the executable contract.

### Commit Messages

Exact analyzer rules stay in the executable harness. This domain is the one exception. No format command repairs a
commit message, no source file holds it, and a published one cannot be rewritten. An agent satisfies the policy only
while writing the message, so the policy is declared here in full.

| Rule | Value |
| --- | --- |
| Header | `type: subject` or `type(scope): subject`, and nothing after the type but the scope |
| Type | one of `build`, `chore`, `ci`, `docs`, `feat`, `fix`, `perf`, `refactor`, `test` |
| Scope | optional, and when present drawn from `a-z`, `0-9`, `.`, and `-` |
| Subject | 15 to 72 characters, counted after the `: ` that closes the header prefix |
| Subject ending | no trailing period |
| Junk words | `wip`, `tmp`, `temp`, `misc`, `stuff`, `asdf`, `fixup`, as whole words anywhere in the header |
| Body | required when the commit changes more than 2 files or more than 50 added plus deleted lines |
| Body separator | everything after the first line, stripped, and a blank line after it is the Git convention |
| Typography | plain ASCII only: no em dash, en dash, one-character ellipsis, or curly quotation mark |
| Attribution | no marker naming an AI agent or an agent session, in any commit |

No commit shape is exempt from the table above, including the headers Git writes by default for a merge
and for a revert. Every tool that makes one of those offers to edit the message, so a conforming message
is always reachable: a revert says what it undid and why, like any other change. A breaking change is
described in the body rather than marked in the header, because a marker a reader has to notice says less
than a sentence saying what breaks. The attribution ban binds in prose as well as by pattern: a marker
that no pattern yet knows is still a violation.

Four traps account for most failures:

- The junk-word scan reads the whole header, so `chore(temp): rotate the build cache keys` fails on its scope.
- The 15-character floor rejects a short subject such as `fix typo`.
- The body threshold counts added and deleted lines together, so a routine change crosses it quickly.
- The attribution and typography scans read the body as well as the header, so a signature or an em dash in an
  explanation fails exactly as it would in a subject.

Check a commit as soon as it is recorded, with
`mvn airness:commit-history airness:commit-typography airness:linear-history`. All three goals read the repository
rather than the module, so none of them needs a build, and an unpublished commit can still be amended.

### Assertions

The harness checks that every test reaches an assertion and that no assertion compares one written-out value with
another. Neither check decides the question those two stand in for, which is whether the assertion would notice if
the behaviour went missing. That half binds in prose: a test fails when the behaviour its name states is removed
from the code under test. Write each test so that it would, and read it back that way before finishing.

The search for an assertion stops at the file boundary, so a test that reaches one only through a helper in another
type reports as unproven. Move the assertion into the test's own source rather than suppressing the finding.

### History Shape

The history is linear, so a merge commit is prohibited wherever it sits, including between two side branches. A
branch takes the work of another by `git rebase` or `git cherry-pick`. The check counts the parents Git recorded
rather than reading the header, so a subject that mentions merging is an ordinary commit, and rewording a merge does
not hide it.

The rule reaches back to the first commit and has no exception. Avoid a merge rather than plan to repair one: once
it is published, only a fresh history satisfies the rule.

## Layer 4: Command Workflow

1. Read `airness.package.root` from the root `pom.xml` before adding or moving Java packages.
2. Run `mvn airness:assets-sync` only to create or restore managed repository files, then review and commit its edits.
3. Run `mvn process-resources -Pformat` to apply formatting and rewrite recipes, then review the resulting source.
4. Run `mvn clean package` for Default verification.
5. Run `mvn clean package -Pextended` before finishing. Extended verification includes Default verification and adds
   the known-vulnerability scan and the history, secret, and Qodana checks. Default verification alone never reads the
   vulnerability database.

## Layer 5: Exceptions and Repairs

- Fix a managed-file finding with `mvn airness:assets-sync`. A project may take over an eligible path through
  `airness.assets.unmanaged` only when the assignment explicitly requires it and the project records the reason.
- Fix formatter and rewrite findings with the format command. Fix compilation, analysis, dependency, test, coverage,
  history, security, and tool failures at their source; do not disable the failing check.
- Read a resource the JAR carries by resolving `Class.getResource` through `Optional.ofNullable(...)` and
  `orElseThrow(...)` to a `URL`, and only then opening the stream in a plain try-with-resources. A stream opened
  before the missing-resource check, or checked inside the resource specifier, is unclosed on the throwing edge,
  which is the `OBL_UNSATISFIED_OBLIGATION_EXCEPTION_EDGE` finding. A `URL` carries no close obligation, so
  resolving it first puts the check where nothing is open yet.
- Weaken a parameter when `TypeMayBeWeakened` asks, and answer the two findings it raises that no weakening can
  satisfy by changing the declaration rather than by suppressing the rule. Give a record its defensive copy through
  a compact constructor, which declares no parameter list at all, rather than through a canonical constructor whose
  parameters are pinned to the component types. Name the first navigation off a parameter that the body only ever
  reads as the qualifier of a further call, rather than chaining it. Such a parameter has no declared expected type
  at its single use, which is the one place this inspection cannot rule a weakening out, so a Jackson `JsonNode`
  reads as weakenable to `TreeNode` even though `TreeNode` returns `TreeNode` and declares no value reader and the
  weakening does not compile. Naming the first hop supplies the expected type and the inspection then rules it out
  itself. A local already carries its initializer's type, so only parameters need this. Neither repair costs any
  coverage, and neither needs an entry in the profile: prefer both to `stopClasses`, which would exempt a type
  everywhere and so give up the genuine weakenings the same run still reports.
- Change an exclusion or the default test timeout only for an explicit project requirement. Never use one merely to
  obtain a pass.
- The test order seed and the suppression ceiling are the harness's own and take no project setting. Answer a
  suppression-ceiling finding by removing a suppression, and never by trying to raise the ceiling.
- For a source suppression, put `@SuppressWarnings` and a non-empty `@Justification` on the same declaration and use
  the analyzer's exact rule ID. For a tool without source suppressions, use its Airness-owned configuration or
  baseline mechanism and keep the reason beside the entry.

### Project Settings

| Setting | Use |
| --- | --- |
| `airness.package.root` | the prefix of every production and test package, and the only required key |
| `airness.assets.unmanaged` | repository paths the project takes over from the harness |
| `airness.typography.excludes` | repository path prefixes the typography scan skips |
| `airness.coverage.excluded.classes` | qualified class patterns the coverage floors skip |
| `airness.test.timeout` | the ceiling on one test, and `30 s` unless set |
| `airness.dependency-check.suppression.file` | a local OWASP suppression file, described below |

Those six are the whole of what a project file may declare. Every other name under `airness.`, and `skipTests`,
`maven.test.skip`, and `jacoco.dataFile`, is refused there, because each of them can decide a verdict. The refusal
reads the file as written, so a name inside a profile that is never activated is refused too. Both
`-Dairness.enforce=false` and `-DskipTests` are command-line flags and are never written into a project file.

Reach for the suppression file only when no upgrade answers an advisory. Keep it at
`.airness/dependency-check-suppressions.xml`, which is the path the scan reads: the document's presence there is what
puts it in front of the scan, so one kept elsewhere is never read. Every rule in it names the advisory it excuses,
says in its `notes` why this project cannot reach the vulnerability, and carries a `YYYY-MM-DD` date. A rule that
suppresses nothing fails the build, like every other exclusion that reaches nothing.
