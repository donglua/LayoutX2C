# LayoutX2C 1.0.0

LayoutX2C 1.0.0 is the first stable release of the compile-time XML layout to Kotlin generation pipeline. It keeps the conservative contract: supported XML is generated into code, unsupported semantics fall back to platform inflation, and unsupported layouts should not break application builds.

## Highlights

- Stable runtime-facing API boundary with `@PublicApi` and `@ExperimentalApi` annotations.
- Gradle plugin setup for Android app and library modules, including automatic KSP and runtime dependencies.
- KSP layout discovery through `@FastLayoutConfig`, `@FastLayouts`, and `@FastLayoutPattern`.
- Per-layout JSON reports and project-level `layoutX2CReport` HTML/JSON summaries for fallback visibility in CI.
- Conservative fallback inflation for unsupported nodes, include/ViewStub cases, and DataBinding semantics.
- Incremental generation cache keyed by layout and resource dependency digests.
- Static generation support for common Android and AndroidX widgets, ConstraintLayout safe subsets, styles, resource references, whitelisted custom views, and whitelisted BindingAdapter calls.

## Compatibility

- Minimum SDK: 23
- Compile SDK: 36
- Android Gradle Plugin: 9.2.1
- Kotlin: 2.2.21
- KSP: 2.3.8
- Java toolchain: 21

## Artifacts

```kotlin
plugins {
    id("io.github.donglua.layoutx2c") version "1.0.0"
}
```

Manual dependency setup:

```kotlin
dependencies {
    implementation("io.github.donglua.layoutx2c:runtime:1.0.0")
    ksp("io.github.donglua.layoutx2c:ksp-processor:1.0.0")
}
```

## Documentation

- Usage and support matrix: `README.md`
- Migration notes: `docs/MIGRATION_1_0.md`
- Benchmark guidance: `docs/BENCHMARKS.md`
- Release process: `docs/RELEASE.md`
