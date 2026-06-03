# LayoutX2C Release Guide

LayoutX2C 1.0 artifacts are released from a tag that matches
`[0-9]*.[0-9]*.[0-9]*`, for example `1.0.0`.

## Required Secrets

Maven Central publishing requires:

- `MAVEN_CENTRAL_USERNAME`
- `MAVEN_CENTRAL_PASSWORD`
- `SIGNING_IN_MEMORY_KEY`
- `SIGNING_IN_MEMORY_KEY_PASSWORD`

Gradle Plugin Portal publishing additionally requires:

- `GRADLE_PUBLISH_KEY`
- `GRADLE_PUBLISH_SECRET`

If the Gradle Plugin Portal secrets are missing, the release workflow publishes
Maven artifacts and skips plugin portal publishing with an explicit message.
Maven Central and signing secrets are required for the release workflow.

## Local Verification

Run the local gates before tagging:

```bash
./gradlew test
./gradlew :demo:assembleDebug
./gradlew :runtime:assembleRelease
./gradlew :gradle-plugin:test
```

Verify local publication metadata without remote credentials:

```bash
./gradlew publishToMavenLocal -Playoutx2c.enablePublishing=true --no-configuration-cache
```

## Tag Release

After local verification passes:

```bash
git tag 1.0.0
git push origin 1.0.0
```

The GitHub Actions release workflow then runs:

1. JVM tests and Android assemble tasks.
2. Android test APK assembly.
3. Maven Central publish for `runtime`, `compiler-core`, `ksp-processor`, and
   `gradle-plugin`.
4. Gradle Plugin Portal publish when plugin portal secrets are configured.

## Failure Boundaries

Repository readiness can be verified locally. Final release can still fail for
external reasons such as invalid Maven Central credentials, expired signing
keys, Gradle Plugin Portal account configuration, or temporary repository
availability. Fix those in the release environment and rerun the workflow.
