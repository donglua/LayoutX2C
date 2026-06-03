# LayoutX2C Benchmarks

The demo app contains a benchmark screen that compares platform inflate and
LayoutX2C generated inflate for the sample layouts listed in `README.md`.

## What To Measure

Use the same build variant and device for every comparison. Record:

- Generated inflate time.
- Platform inflate time.
- Allocation or memory deltas if the profiler is attached.
- Build time overhead for KSP generation.
- Fallback count from the LayoutX2C report.

## How To Run

Build and install the demo app:

```bash
./gradlew :demo:assembleDebug
```

Open the demo app and choose the benchmark screen. Run each layout several
times, discard the first warm-up run, and compare medians rather than a single
sample.

Generate the compile report:

```bash
./gradlew :app:layoutX2CReport
```

The repository demo module wires KSP directly so it can use project
dependencies during development. The `layoutX2CReport` task is provided by the
published Gradle plugin and is covered by the plugin functional tests:

```bash
./gradlew :gradle-plugin:test
```

Measure build overhead by comparing clean or cache-controlled builds with the
same Gradle daemon and Android SDK state:

```bash
./gradlew clean :demo:assembleDebug
./gradlew :demo:assembleDebug
```

## Reporting Results

Benchmark notes should include:

- Device model and Android version.
- Build variant.
- Git commit.
- AGP, Kotlin, and KSP versions.
- Whether Gradle build cache was warm.
- Which layouts were FULL, PARTIAL, or FALLBACK in the report.

## Limits

Local numbers are useful for regression checks, not universal claims. Inflater
cost depends on device CPU, theme complexity, resource qualifiers, AppCompat
behavior, and layout shape. LayoutX2C's policy is still correctness first:
fallback is preferred when static generation cannot preserve semantics.
