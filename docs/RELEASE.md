# LayoutX2C Release Guide

LayoutX2C artifacts are released from a SemVer tag such as `1.0.0-rc.1` or
`1.0.0`.

## Required Secrets

Maven Central publishing requires:

- `MAVEN_CENTRAL_USERNAME`
- `MAVEN_CENTRAL_PASSWORD`
- `GPG_PRIVATE_KEY`
- `GPG_PASSPHRASE`
- `GPG_KEY_ID`

Central Portal namespace must be verified for `io.github.donglua`. Published
coordinates use `io.github.donglua.layoutx2c`.

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
./gradlew :demo:assembleRelease :demo:assembleDebugAndroidTest
```

Verify local publication metadata without remote credentials:

```bash
./gradlew publishToMavenLocal -Playoutx2c.enablePublishing=true --no-configuration-cache
```

Build a Central Portal manual upload bundle for the `Publish Component` flow:

```bash
ORG_GRADLE_PROJECT_signingInMemoryKey="$SIGNING_IN_MEMORY_KEY" \
ORG_GRADLE_PROJECT_signingInMemoryKeyPassword="$SIGNING_IN_MEMORY_KEY_PASSWORD" \
./gradlew buildCentralPortalBundle \
  -Playoutx2c.enablePublishing=true \
  --no-configuration-cache
```

Upload `build/central-portal/layoutx2c-1.0.0-central-bundle.zip` in Central
Portal's `Publish Component` page. The bundle is a Maven Repository Layout zip
and does not require `MAVEN_CENTRAL_USERNAME` or `MAVEN_CENTRAL_PASSWORD` until
it is uploaded through Central Portal or published by CI.

GitHub Actions imports `GPG_PRIVATE_KEY` and publishes with Gradle's GPG command
signing path:

```bash
./gradlew publishAndReleaseToMavenCentral \
  -Playoutx2c.enablePublishing=true \
  -Playoutx2c.useGpgSigning=true \
  -Psigning.gnupg.keyName="$GPG_KEY_ID" \
  -Psigning.gnupg.passphrase="$GPG_PASSPHRASE" \
  -PmavenCentralDeploymentValidation=PUBLISHED \
  --no-configuration-cache
```

When a device or emulator is available, run connected Android equivalence tests:

```bash
./gradlew :demo:connectedDebugAndroidTest
```

Capture benchmark results with the template in `BENCHMARKS.md` before publishing
release notes.

## Tag Release

After local verification passes:

```bash
git tag -a 1.0.0 -m "Release 1.0.0"
git push origin 1.0.0
```

The GitHub Actions release workflow then runs:

1. JVM tests and Android assemble tasks.
2. Android test APK assembly.
3. Maven Central publish for `runtime`, `compiler-core`, `ksp-processor`, and
   `gradle-plugin`.
4. Gradle Plugin Portal publish when plugin portal secrets are configured.

## Publish GitHub Release

Pushing the git tag triggers the workflow, but it does not by itself guarantee a
public GitHub Release page. After the workflow succeeds:

```bash
gh release create 1.0.0 --title "LayoutX2C 1.0.0" --notes-file docs/RELEASE_NOTES_1_0.md
gh release view 1.0.0 --json isDraft,isPrerelease,url
```

If the release page already exists, update it instead:

```bash
gh release edit 1.0.0 --title "LayoutX2C 1.0.0" --notes-file docs/RELEASE_NOTES_1_0.md
gh release view 1.0.0 --json isDraft,isPrerelease,url
```

The stable release should report `isDraft=false` and `isPrerelease=false`.

## Failure Boundaries

Repository readiness can be verified locally. Final release can still fail for
external reasons such as invalid Maven Central credentials, expired signing
keys, Gradle Plugin Portal account configuration, or temporary repository
availability. Fix those in the release environment and rerun the workflow.
