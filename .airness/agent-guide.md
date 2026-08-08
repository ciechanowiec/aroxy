# Airness Agent Guide

Apply these layers in order. Each layer narrows the preceding layer without weakening it. The guide describes every
rule at the abstraction level of its layer; exact analyzer rules remain in the executable harness.

## Layer 1: Governing Standard

- Before changing the repository, read and follow `README-guideline-software-project.adoc`. It governs the complete
  repository: organization, code, tests, dependencies, verification, and history.
- Follow the project-owned instructions in the root, cross-agent `AGENTS.md` as well. They may strengthen or
  specialize the governing standard but may not weaken it.
- Satisfy both written instructions and executable checks. A passing check does not cancel a written rule that the
  harness cannot verify mechanically.

## Layer 2: Harness Invariants

- The project inherits `airness-parent`. Do not weaken, replace, duplicate, or version the plugins, dependencies,
  analyzers, rules, thresholds, or managed files that the parent owns.
- Use exactly Java 25, Maven 3.9.16 or later, and a Git working tree. Keep every production and test package under the
  `airness.package.root` declared in the root `pom.xml`.
- Treat every finding and every tool, setup, or compilation failure as a failed verification. Reporting findings with
  `-Dairness.enforce=false` is not a pass. A build using `-DskipTests` produces no Airness verdict.
- Verification must describe the existing repository state. Only the synchronization and formatting commands below
  may edit the working tree, and their edits must be reviewed.

## Layer 3: Governed Domains

Airness governs all of the following domains:

- **Toolchain and Maven model:** runtime versions, project and parent coordinates, package ownership, inherited plugin
  ownership, dependency convergence, and valid Airness parameters.
- **Repository files and instructions:** managed, seeded, and forbidden files; agent entry files; editor and Git
  configuration; and an unchanged committable tree during verification.
- **Source:** formatting, imports, modernization recipes, compilation, nullness, static analysis, documentation
  comments, source comments, and typography.
- **Dependencies:** declaration hygiene, unused dependencies, licenses, available stable updates, and the maximum
  permitted freshness gap.
- **Tests and evidence:** test execution, production-to-test boundaries, instruction and branch coverage at the
  declared scope, current-build coverage evidence, mutation analysis, and accepted mutation survivors.
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
- Change typography exclusions, coverage exclusions, mutation exclusions, mutation threads, or mutation timeouts only
  for an explicit project requirement. Never use an exclusion or a larger timeout merely to obtain a pass.
- Use a suppression only for a rule that genuinely does not apply. Put `@SuppressWarnings` and a non-empty
  `@Justification` on the same declaration, use the analyzer's exact rule ID, match the suppression scope to the
  justification scope, and remove the pair when the warning no longer occurs.

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
