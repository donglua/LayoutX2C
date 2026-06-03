# LayoutX2C 1.0 Repository Readiness Design

## Goal

Bring LayoutX2C to a repository-ready 1.0 state based on `docs/ROADMAP.md`.
The final repository state should be suitable for a conservative production
adoption path and for an external release operator to publish 1.0.0 using tag
and secret-managed release workflows.

This work does not assume access to Maven Central, Gradle Plugin Portal, signing
keys, or connected Android devices. Those external release and device steps must
remain documented and represented in CI, but local completion is proven by code,
tests, assemble tasks, report generation, and release configuration validation.

## Scope

### Phase 1: Precise Resource Digest Graph

`LayoutX2CDigestCalculator` currently hashes all `res/values/*.xml` files for
every layout. Replace this coarse dependency with a per-layout resource graph:

- Scan layout XML and recursively included layouts for `@string`, `@dimen`,
  `@color`, `@drawable`, `@mipmap`, and `@style` references in attributes.
- Resolve value resources from `res/values*.xml` by resource type and name.
- Recursively include resource references found in resolved value items.
- Hash only referenced value resources and referenced file resources.
- Keep layout/include/ViewStub dependencies in the existing layout dependency
  graph.
- Treat unresolved, malformed, or dynamic references conservatively: include a
  stable unresolved marker in the digest and keep generation semantics unchanged.
- Do not attempt to compile `?attr` or theme semantics into constants for 1.0.

The graph only narrows cache invalidation. It must not make unsupported style or
theme behavior appear generated-safe.

### Phase 2: Public API Boundary

Freeze the runtime-facing 1.0 API surface:

- Add runtime annotations for `@PublicApi` and `@ExperimentalApi`.
- Mark stable entry points: `LayoutFactory`, `LayoutX2CRegistry`,
  `LayoutX2CFactory2`, `FallbackInflater`, `FallbackChildNavigator`, and
  configuration annotations.
- Mark opt-in or diagnostic surfaces as experimental if they may change after
  1.0.
- Add a migration guide describing the 1.0 API boundary and breaking-change
  policy.

Generated code can use public runtime APIs. Internal implementation details
should stay unannotated or explicitly internal to Kotlin where possible.

### Phase 3: Publishing Readiness

Make release engineering explicit and locally verifiable:

- Update project version to `1.0.0`.
- Ensure Maven Central publishing covers runtime, compiler-core, ksp-processor,
  and gradle-plugin.
- Ensure Gradle Plugin Portal publishing remains wired through secrets.
- Add release documentation that lists required secrets, local dry-run commands,
  tag format, and verification gates.
- Add or verify consumer ProGuard/R8 rules for generated registry discovery and
  runtime APIs.
- Keep external publish execution outside local completion unless credentials
  are available.

### Phase 4: Compatibility and Documentation

Document the production adoption contract:

- README and ROADMAP describe completed 1.0 support, remaining non-goals, and
  the precise resource graph behavior.
- Compatibility matrix covers AGP 8.4+ / 9.x, KSP 2.x, Android API 21-35, and
  ViewBinding/DataBinding coexistence.
- Benchmark documentation explains what is measured, how to run it, and the
  limitations of local measurements.
- Demo remains the sample surface for generated inflate, fallback, DataBinding
  binding subclasses, and reports.

## Commit Plan

1. `docs: add 1.0 readiness design`
2. `test(ksp): specify precise resource digest dependencies`
3. `feat(ksp): hash precise resource dependencies`
4. `feat(runtime): mark 1.0 public api`
5. `docs: document 1.0 migration and compatibility`
6. `build: prepare 1.0 publishing`
7. `docs: update roadmap for 1.0 readiness`

Implementation commits may split further if a phase becomes large, but each
commit must leave the JVM test suite green.

## Tests And Verification

Required local verification:

- `./gradlew test`
- `./gradlew :demo:assembleDebug`
- `./gradlew :runtime:assembleRelease`
- `./gradlew :demo:layoutX2CReport`
- Targeted tests for resource digest behavior before and after implementation.

Device or CI verification:

- `./run_android_tests.sh` or equivalent connected Android tests for generated
  vs platform-inflated equivalence.
- Release workflow execution on a 1.0.0 tag with configured Maven Central,
  signing, and optional Gradle Plugin Portal secrets.

## Non-Goals

- Full style/theme code generation.
- Converting the KSP processor from aggregating to isolating.
- Publishing artifacts without external credentials.
- Expanding ConstraintLayout beyond the current safe subset.
- Replacing full DataBinding runtime semantics.

## Risks

- Android resource resolution is richer than XML text scanning. The digest graph
  must be conservative when references are ambiguous.
- Values qualifiers can affect runtime resource selection. The graph should
  include all matching `values*` definitions for a referenced key rather than
  pretending to choose a device-specific value.
- File resources may contain nested references or platform semantics that are
  not statically understood. Hashing the file content and unresolved markers is
  safer than partial interpretation.
- Publishing plugins can fail for account or credential reasons even when the
  repository configuration is correct; documentation must make that boundary
  explicit.
