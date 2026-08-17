# Airness Agent Guide

This guide maps the governing standard to the Airness harness. After it, continue through the lower `AGENTS.md`
layers in the order the standard declares. Exact analyzer rules remain in the executable harness.

## Layer 1: Governing Standard

- Read `README-guideline-software-project.adoc` before using this Airness-specific mapping.

## Layer 2: Harness Invariants

- The project inherits `airness-parent`. Do not weaken, replace, duplicate, or version the plugins, dependencies,
  analyzers, rules, thresholds, or managed files that the parent owns.
- Use exactly Java 25, Maven 3.9.16 or later, and a Git working tree. Keep every production and test package under the
  `airness.package.root` declared in the root `pom.xml`.
- Treat every finding and every tool, setup, or compilation failure as a failed verification. Reporting findings with
  `-Dairness.enforce=false` is not a pass. A build using `-DskipTests` produces no Airness verdict.

## Layer 3: Governed Domains

Airness governs all of the following domains:

- **Toolchain and Maven model:** runtime versions, project and parent coordinates, package ownership, inherited plugin
  ownership, raw-model anti-bypass checks, effective dependency convergence, and valid Airness parameters.
- **Repository files and instructions:** managed, seeded, and forbidden files; agent instruction files; editor and Git
  configuration; and an unchanged committable tree during verification.
- **Source:** formatting, imports, modernization recipes, compilation, nullness, static analysis, documentation
  comments, source comments, and typography.
- **Dependencies:** explicit scopes, exactly named versions, no project-declared repositories or system paths,
  released dependencies for a released project, one version and one owning artifact per class, unused dependencies,
  licenses, available stable updates, and the maximum permitted freshness gap.
- **Artifacts:** the finished JAR contains no unsafe or duplicate paths, development or source files, test-only output,
  machine-local repository paths, or recognizable secret material.
- **Tests and evidence:** test execution, test integrity and determinism, a default timeout on every test,
  production-to-test boundaries, per-class line and branch coverage, and current-build coverage evidence.
- **Repository assurance:** secret scanning, Qodana analysis, complete Git history, commit-message policy, commit
  typography, linear history, and history-wide compliance.

Do not treat a domain omitted from one command's output as ungoverned. The parent POM, bundled configurations, and
Airness goals together define the executable contract.

### Commit Messages

Exact analyzer rules stay in the executable harness. This domain is the one exception. No format command repairs a
commit message, no source file holds it, and a published one cannot be rewritten. An agent satisfies the policy only
while writing the message, so the policy is declared here in full.

| Rule | Value |
| --- | --- |
| Header | `type(scope): subject`, with an optional `!` before the colon for a breaking change |
| Type | one of `feat`, `fix`, `docs`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert` |
| Scope | optional, and when present drawn from `a-z`, `0-9`, `.`, and `-` |
| Subject | 15 to 72 characters, counted after the `: ` that closes the header prefix |
| Subject ending | no trailing period |
| Junk words | `wip`, `tmp`, `temp`, `misc`, `stuff`, `asdf`, `fixup`, as whole words anywhere in the header |
| Body | required when the commit changes more than 2 files or more than 50 added plus deleted lines |
| Body separator | one blank line after the header, since the body is everything after the first blank line |
| Typography | plain ASCII only: no em dash, en dash, one-character ellipsis, or curly quotation mark |
| Attribution | no marker naming an AI agent or an agent session, in any commit |

A revert commit keeps the `Revert "..."` form Git writes for it and is exempt from the header shape alone. A merge
commit is exempt too, but only because it is prohibited outright and the History Shape rules below report it once
rather than burying that verdict under header findings. The attribution ban holds for every commit whatever its
header, and it binds in prose as well as by pattern: a marker that no pattern yet knows is still a violation.

Four traps account for most failures:

- The junk-word scan reads the whole header, so `chore(temp): rotate the build cache keys` fails on its scope.
- The 15-character floor rejects a short subject such as `fix typo`.
- The body threshold counts added and deleted lines together, so a routine change crosses it quickly.
- An explanation written without a blank line after the header does not count as a body.

Check a commit as soon as it is recorded, with
`mvn airness:commit-history airness:commit-typography airness:linear-history`. All three goals read the repository
rather than the module, so none of them needs a build, and an unpublished commit can still be amended.

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
   history, secret, and Qodana checks.

## Layer 5: Exceptions and Repairs

- Fix a managed-file finding with `mvn airness:assets-sync`. A project may take over an eligible path through
  `airness.assets.unmanaged` only when the assignment explicitly requires it and the project records the reason.
- Fix formatter and rewrite findings with the format command. Fix compilation, analysis, dependency, test, coverage,
  history, security, and tool failures at their source; do not disable the failing check.
- Change typography exclusions, coverage exclusions, or the default test timeout only for an explicit project
  requirement. Never use an exclusion or a larger timeout merely to obtain a pass.
- For a source suppression, put `@SuppressWarnings` and a non-empty `@Justification` on the same declaration and use
  the analyzer's exact rule ID. For a tool without source suppressions, use its Airness-owned configuration or
  baseline mechanism and keep the reason beside the entry.

