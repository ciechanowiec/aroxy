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
  production-to-test boundaries, per-class line and branch coverage, current-build coverage
  evidence, mutation analysis, and accepted mutation survivors.
- **Repository assurance:** secret scanning, Qodana analysis, complete Git history, commit-message policy, commit
  typography, and history-wide compliance.

Do not treat a domain omitted from one command's output as ungoverned. The parent POM, bundled configurations, and
Airness goals together define the executable contract.

## Layer 4: Command Workflow

1. Read `airness.package.root` from the root `pom.xml` before adding or moving Java packages.
2. Run `mvn airness:assets-sync` only to create or restore managed repository files, then review and commit its edits.
3. Run `mvn process-resources -Pformat` to apply formatting and rewrite recipes, then review the resulting source.
4. Run `mvn clean package` for Default verification.
5. Run `mvn clean package -Pextended` before finishing. Extended verification includes Default verification and adds
   history, mutation, secret, and Qodana checks.

## Layer 5: Exceptions and Repairs

- Fix a managed-file finding with `mvn airness:assets-sync`. A project may take over an eligible path through
  `airness.assets.unmanaged` only when the assignment explicitly requires it and the project records the reason.
- Fix formatter and rewrite findings with the format command. Fix compilation, analysis, dependency, test, coverage,
  history, security, and tool failures at their source; do not disable the failing check.
- Change typography exclusions, coverage exclusions, mutation exclusions, mutation threads, mutation timeouts, or the
  default test timeout only for an explicit project requirement. Never use an exclusion or a larger timeout merely to
  obtain a pass.
- For a source suppression, put `@SuppressWarnings` and a non-empty `@Justification` on the same declaration and use
  the analyzer's exact rule ID. For a tool without source suppressions, use its Airness-owned configuration or
  baseline mechanism and keep the reason beside the entry.

### Mutation Baseline

- Keep `mutation-baseline.tsv` in every module to which the inherited mutation analysis applies, even when the file
  is empty.
- Copy each accepted survivor from `target/pit-reports/mutations.xml` as three tab-separated fields: the fully
  qualified class, method, and mutation description. Add a fourth field that explains why the mutation is accepted.
- Treat a survivor missing from the baseline as a test gap. Remove a baseline entry when the tests detect its
  mutation.
- Start the reason with `[intermittent]` only when tests sometimes detect the mutation and sometimes miss it.
- Treat a mutation run that produces no mutations as a failure.

```text
com.example.orders.ClockAdapter	now	removed call to java/time/Clock::instant	Covered by an external platform test
```
