# LayoutX2C Release Guide

LayoutX2C artifacts are released from an explicit SemVer tag such as
`1.4.1-rc.1` or `1.4.1`. The effective artifact version, git tag, release notes,
and published coordinates must agree.

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
the required Maven artifacts and explicitly skips Plugin Portal publication.
Maven Central and signing secrets remain required.

## Local Release Gate

Run the deterministic repository gate before tagging. `VERSION=1.4.1` is the
next candidate example; replace it with the intended tag:

```bash
VERSION=1.4.1
./gradlew releaseCheck \
  -Playoutx2c.version="$VERSION" \
  --no-configuration-cache \
  --no-daemon
```

The gate verifies version and documentation inputs before running JVM and
Android tests, coverage reports, Android assembly, local Maven publication,
published POM coordinates, and the standalone consumer smoke project. It does
not publish remotely or require release credentials.

When a device or emulator is available, also run connected Android equivalence
tests:

```bash
./gradlew :demo:connectedDebugAndroidTest
```

Capture benchmark results with the [benchmark template](BENCHMARKS.md) when a
release changes generated inflation or fallback performance.

## Optional Publication Checks

Verify local publication metadata without remote credentials:

```bash
./gradlew publishToMavenLocal \
  -Playoutx2c.version="$VERSION" \
  -Playoutx2c.enablePublishing=true \
  --no-configuration-cache
```

To build a Central Portal manual-upload bundle for the `Publish Component`
flow:

```bash
VERSION="$VERSION" \
ORG_GRADLE_PROJECT_signingInMemoryKey="$SIGNING_IN_MEMORY_KEY" \
ORG_GRADLE_PROJECT_signingInMemoryKeyPassword="$SIGNING_IN_MEMORY_KEY_PASSWORD" \
./gradlew buildCentralPortalBundle \
  -Playoutx2c.enablePublishing=true \
  --no-configuration-cache
```

Upload `build/central-portal/layoutx2c-${VERSION}-central-bundle.zip` in Central
Portal. The bundle is a Maven Repository Layout zip.

GitHub Actions imports `GPG_PRIVATE_KEY` and uses Gradle's GPG command signing
path:

```bash
VERSION="$VERSION" ./gradlew publishAndReleaseToMavenCentral \
  -Playoutx2c.enablePublishing=true \
  -Playoutx2c.useGpgSigning=true \
  -Psigning.gnupg.keyName="$GPG_KEY_ID" \
  -Psigning.gnupg.passphrase="$GPG_PASSPHRASE" \
  -PmavenCentralDeploymentValidation=PUBLISHED \
  --no-configuration-cache
```

## Tag Release

Confirm that the stable base-version notes exist before creating the tag:

```bash
VERSION=1.4.1
NOTES_VERSION="${VERSION%%-*}"
NOTES_FILE="docs/releases/${NOTES_VERSION}.md"
test -f "$NOTES_FILE"

git tag -a "$VERSION" -m "Release $VERSION"
git push origin "$VERSION"
```

The release workflow is responsible for:

1. Checking out the requested tag and rerunning `releaseCheck`.
2. Validating signing and publishing credentials only after the repository gate.
3. Publishing `runtime`, `compiler-core`, `ksp-processor`, and `gradle-plugin`
   to Maven Central.
4. Publishing to the Gradle Plugin Portal when its credentials are configured.
5. Verifying public Maven coordinates and creating the GitHub Release from
   `docs/releases/${NOTES_VERSION}.md`.

## Post-Publication Verification

Maven Central synchronization is part of release completion, not an assumed
side effect. Verify all four artifacts and the Gradle plugin marker:

```bash
./scripts/verify-maven-release.sh "$VERSION"
```

Then confirm the GitHub Release state:

```bash
gh release view "$VERSION" --json isDraft,isPrerelease,url
```

A stable release must report `isDraft=false` and `isPrerelease=false`. A version
with a pre-release suffix must be marked as a prerelease.

## Failure Boundaries

Repository readiness can be verified locally. Final release can still fail for
external reasons such as invalid Maven Central credentials, expired signing
keys, Gradle Plugin Portal account configuration, or temporary repository
availability. Fix the release environment and rerun the workflow; do not delete
or recreate a valid published tag as rollback logic.
