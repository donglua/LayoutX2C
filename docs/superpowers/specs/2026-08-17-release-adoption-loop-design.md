# LayoutX2C 1.x Release and Adoption Loop Design

## Goal

Turn the existing 1.0 repository readiness work into a repeatable release and
consumer-adoption loop. A release candidate must be verifiable as the same
artifacts that a clean Android project will consume, and project documentation
must distinguish released, tested, and planned behavior.

This is the first of four improvement projects. Correctness matrix expansion,
core generator refactoring, and data-driven feature growth remain separate
follow-up projects so that the release baseline stays stable while this work is
implemented.

## Current State

- Tag `1.0.0` exists and points to the first stable release commit.
- `main` is 27 commits past `1.0.0`, including backward-compatible features,
  fixes, tests, and reporting improvements.
- The root build and `LayoutX2CPlugin.VERSION` both still hard-code `1.0.0`.
  This allows the plugin artifact version and its injected runtime/processor
  dependency versions to drift.
- `docs/ROADMAP.md` still describes 1.0 as waiting for external release and
  links to two documents that were deliberately removed after the release.
- CI validates JVM tests and coverage on one toolchain. The release workflow
  assembles Android artifacts, but it does not prove that a standalone project
  can resolve and use the locally published plugin marker, runtime, and
  processor together.
- Compatibility claims currently mix intended support with combinations that
  CI actually verifies. In particular, the repository uses minSdk 23 and
  compileSdk 36 while the roadmap still mentions API 21-35.

## Scope

### 1. Version Ownership

Use the root Gradle project version as the only artifact version source.

- The development default becomes `1.1.0-SNAPSHOT`. The minor version reflects
  the backward-compatible capabilities added since 1.0.0.
- The Gradle property `layoutx2c.version` is the local candidate override, and
  `VERSION` remains the release workflow override. If both are present and
  differ, configuration fails. A release build for tag `1.1.0` therefore
  publishes `1.1.0`, while ordinary local builds remain snapshots.
- Remove the literal version from `LayoutX2CPlugin`. Generate a small plugin
  resource from `project.version` during `processResources`, and load it when
  adding `runtime` and `ksp-processor` dependencies.
- Missing or malformed generated version metadata is a plugin application
  error with an actionable message. The plugin must never silently fall back to
  an unrelated release.
- Add a test proving that all publications and plugin-injected coordinates use
  the same requested version.

This design does not introduce a version catalog or an external release tool.
The repository has one product version, so a provider-backed root Gradle value
is sufficient.

### 2. Versioned Release Documentation

Replace disposable root-level release documents with stable versioned history:

- `docs/releases/1.0.0.md` archives the published 1.0.0 notes from the release
  tag, corrected only where they claimed features that landed later.
- `docs/migrations/1.0.md` records the stable API boundary introduced by 1.0.
- `docs/releases/1.1.0.md` describes the user-visible changes since 1.0.0 and
  serves as the GitHub Release notes source when 1.1.0 is published.
- `CHANGELOG.md` is a compact index linking to versioned release notes rather
  than duplicating their full contents.
- `docs/ROADMAP.md` records 1.0 as released, describes 1.1 as the current
  development line, and removes the claim that external 1.0 publication is the
  only remaining task.
- `docs/RELEASE.md` references versioned notes and separates repository
  readiness, artifact publication, and post-publication verification.

README installation examples use the latest stable released version until a
new release is actually published. Main-branch-only capabilities are labelled
as upcoming 1.1 behavior instead of being presented as available in 1.0.0.

### 3. Release Readiness Gate

Add a root `releaseCheck` lifecycle task. It composes existing build tasks and
new deterministic validations rather than reimplementing Gradle publication
logic in shell scripts.

The gate must verify:

- the effective version is valid SemVer and is not a snapshot for a release;
- the workflow tag or manual version equals the effective project version;
- all publishable modules use the same group and version;
- required POM fields and the Gradle plugin marker are present in a temporary
  Maven repository;
- local Markdown links in README, ROADMAP, release, migration, and changelog
  documents resolve to tracked files;
- JVM tests, Android assembly, report generation, and the standalone consumer
  smoke test pass.

Repository checks may run `releaseCheck` with an explicit candidate version,
but they do not sign or upload artifacts. Signing, credentials, and remote
availability remain release-environment concerns.

### 4. Standalone Consumer Smoke Project

Create `integration-tests/consumer-smoke` as a standalone Android Gradle build,
not a subproject and not a composite build.

The smoke flow is:

1. Publish all four artifacts plus the Gradle plugin marker to an isolated
   repository under the root build directory.
2. Start the standalone build with only Google, Maven Central, Gradle Plugin
   Portal where required, and that isolated repository.
3. Apply `io.github.donglua.layoutx2c` by version.
4. Compile a minimal annotated layout configuration.
5. Assert that the generated factory/registry exists and that
   `layoutX2CReport` produces JSON and HTML output.
6. Run the smoke build with Gradle dependency refresh so a developer cache
   cannot hide missing publication metadata or transitive dependencies.

The fixture must not use `includeBuild`, direct project dependencies, or the
Gradle TestKit plugin classpath. Those mechanisms would bypass the exact
coordinates external users resolve.

The initial smoke project uses the repository's current Android/Kotlin/KSP
toolchain. Broader AGP, KSP, JDK, and Android API combinations belong to the
next correctness and compatibility project.

### 5. CI and Release Flow

Split checks by purpose while keeping each result visible:

- The normal check workflow runs unit tests with coverage and the standalone
  consumer smoke test.
- The release workflow runs `releaseCheck` before secret validation and remote
  publication. Repository failures therefore surface before credentials are
  touched.
- A successful artifact publication is followed by explicit resolution checks
  for each Maven coordinate and the Gradle plugin marker.
- GitHub Release creation happens only after remote verification succeeds and
  uses `docs/releases/<version>.md` as its notes source.
- A failed post-publication check does not attempt destructive rollback. It
  fails with the exact unresolved coordinate and leaves recovery to the
  maintainer because Maven releases are immutable.

The workflow continues to allow manual dispatch, but the supplied version must
match an existing commit tag before a stable release can be published. Numeric
SemVer tags without a `v` prefix remain the canonical format for consistency
with the existing release trigger.

### 6. Adoption-Facing README

Restructure the top of README around the first successful integration path:

- stable version and minimal plugin setup;
- one minimal `@FastLayoutConfig` example;
- command to generate and open the fallback report;
- a short tested-toolchain table that distinguishes current CI coverage from
  intended compatibility;
- stable versus experimental API policy;
- links to release history, migration notes, benchmarks, detailed support
  coverage, and release procedure.

The complete View and attribute list remains available but moves below the
integration and diagnostics path. The README should help a new adopter reach a
working build before asking them to understand every supported XML construct.

## Component Boundaries

### Build Version Provider

Owns the effective development or release version and exposes it to all
subprojects. The generated plugin resource is an output of this provider, not a
second source of truth.

### Release Validation Tasks

Own local, deterministic repository validation. They may inspect generated POM,
plugin marker, documentation links, and publication directories. They do not
perform network access or mutate tags.

### Consumer Smoke Build

Owns external-consumer behavior. It depends only on repository coordinates and
public plugin/API contracts. It must remain small enough that a failure points
to publication wiring rather than demo application complexity.

### Release Workflow

Owns credentials, signing, remote publication, remote resolution checks, and
GitHub Release creation. It invokes repository tasks instead of embedding
product-specific validation rules in YAML.

### Documentation

Owns the public contract at three time scales: README for current adoption,
versioned notes for released history, and ROADMAP for future intent.

## Failure Handling

- Version mismatch: fail before tests or publication and print the effective
  project version, requested release version, and tag.
- Missing plugin version resource: fail plugin application and name the missing
  resource; do not inject `unspecified` or a previous stable version.
- Broken local documentation link: fail `releaseCheck` with source file and
  target path.
- Smoke resolution failure: retain Gradle diagnostics and identify whether the
  plugin marker, runtime, processor, or compiler-core coordinate is absent.
- Missing release secrets: fail only after repository readiness succeeds, using
  the existing explicit secret names.
- Remote propagation delay: retry bounded resolution checks before failing.
  Do not recreate or overwrite an immutable Maven release.

## Testing Strategy

### Unit and Functional Tests

- Test version parsing and release/tag mismatch diagnostics.
- Extend Gradle plugin functional tests to assert injected dependency versions.
- Test document-link validation with valid, missing, and external links.
- Test publication metadata for all modules and the plugin marker.

### Integration Test

- Publish to an isolated local Maven repository.
- Build `integration-tests/consumer-smoke` with refreshed dependencies.
- Verify generated source/classes and report JSON/HTML outputs.
- Run from both local `releaseCheck` and CI.

### Existing Gates

- Preserve `test`, Kover XML/HTML generation, demo debug/release assembly,
  runtime release assembly, and Android test APK assembly.
- Connected generated-versus-inflated tests remain optional in this phase and
  become a required matrix concern in the next project.

## Delivery Sequence

1. Introduce the single version provider and eliminate plugin version drift.
2. Add local publication metadata assertions and the standalone consumer smoke
   project.
3. Add `releaseCheck` and wire normal/release CI around it.
4. Repair and version release documentation, then restructure README.
5. Prepare 1.1.0 release notes and verify the complete gate with a candidate
   version.

Each step must leave ordinary development tests green. Documentation is updated
after task names and outputs stabilize, so commands shown to adopters are
executable rather than aspirational.

## Acceptance Criteria

- No production source file contains a second literal artifact version used for
  dependency injection.
- `./gradlew releaseCheck -Playoutx2c.version=1.1.0` validates the candidate
  without signing or network publication.
- A standalone Android project resolves the locally published plugin and all
  transitive LayoutX2C artifacts, generates code, and produces both report
  formats.
- README documents the latest actually published version and its integration
  path; unreleased behavior is visibly labelled.
- ROADMAP, release instructions, changelog, migration notes, and release notes
  contain no broken local links or contradictory 1.0 status.
- CI checks consumer resolution on every pull request and release readiness
  before using publishing secrets.
- The release workflow can publish and then create a GitHub Release only after
  remote coordinates are resolvable.

## Non-Goals

- Triggering the real 1.1.0 tag or remote publication without a separate
  maintainer decision.
- Expanding the supported View, attribute, DataBinding, style, or theme surface.
- Refactoring `ViewRegistry`, `BindingFacadeGeneratorV2`, or processor
  architecture.
- Claiming a broad compatibility matrix based on one smoke toolchain.
- Adding an IDE plugin, documentation site, telemetry, or automatic updater.

## Risks and Mitigations

- **Nested Gradle builds can accidentally use cached artifacts.** Use an
  isolated repository, explicit version, and dependency refresh; forbid
  composite substitution.
- **Snapshot metadata can make smoke tests nondeterministic.** Use a unique
  isolated repository per build and delete it through Gradle task outputs, not
  ad hoc broad cleanup commands.
- **README may advertise an unpublished version during release preparation.**
  Keep installation snippets on the latest stable version until publication;
  describe 1.1 changes in versioned release notes.
- **Remote repositories may be eventually consistent.** Use bounded retries and
  fail with recovery instructions after publication rather than hiding the
  state.
- **Release automation can overreach.** Local tasks never create tags, publish
  remotely, or create GitHub releases. Those actions remain confined to an
  explicitly triggered workflow with maintainer credentials.
