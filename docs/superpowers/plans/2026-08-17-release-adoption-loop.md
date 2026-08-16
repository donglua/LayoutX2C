# LayoutX2C 1.x Release and Adoption Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the current `1.4.0` repository produce one coherent artifact version, prove the published coordinates from a standalone Android consumer, and enforce release/documentation checks before publication.

**Architecture:** Keep version resolution in the root Gradle build and inject the resolved version into the Gradle plugin as a generated resource. Add a dedicated local Maven repository and standalone consumer fixture that exercise published coordinates, then compose version, metadata, documentation, build, and smoke checks into a root `releaseCheck` task. Keep remote publication and GitHub Release creation in the existing workflow, after the local gate succeeds.

**Tech Stack:** Kotlin DSL, Gradle 9.5.1 wrapper, Android Gradle Plugin 9.3.1, Kotlin 2.2.21, KSP 2.3.8, Gradle TestKit, JUnit 4, Truth, Maven publication metadata, GitHub Actions, and shell-based HTTP verification.

---

## Baseline Reconciliation

The design specification was written before the remote `main` branch advanced
through the `1.1.0`, `1.2.0`, `1.3.0`, and `1.4.0` release work. The approved
outcome is unchanged, but implementation targets the current repository:

- `1.4.0` is the latest published version and remains the README stable version.
- The next development version is `1.4.1-SNAPSHOT`; a release candidate is
  `1.4.1`.
- `gradle-plugin` already has TestKit coverage, including multi-module registry
  behavior, but those tests use `includeBuild` and `withPluginClasspath`; the
  new consumer smoke project must resolve Maven coordinates instead.
- Existing release notes are root-level `docs/RELEASE_NOTES_*.md`; this plan
  moves them to versioned `docs/releases/` paths and updates compatibility
  links without deleting compatibility stubs.
- The existing release workflow already publishes Maven Central and optionally
  the Plugin Portal, but it lacks a repository-local `releaseCheck` gate,
  post-publication coordinate checks, and automatic GitHub Release creation.

## File Map

### Version and Publication

- Modify `build.gradle.kts`: resolve `layoutx2c.version`, `VERSION`, and the
  `1.4.1-SNAPSHOT` default; configure the isolated consumer repository; register
  publication and release validation lifecycle tasks.
- Modify `demo/build.gradle.kts`: use `rootProject.version` for the demo app
  version instead of reading the removed artifact-version extra.
- Modify `runtime/build.gradle.kts`: remove unused artifact-version extras and
  keep runtime publication tied to the root project version.
- Create `gradle-plugin/src/main/resources/com/github/donglua/layoutx2c/plugin/layoutx2c-version.properties`: generated resource template containing the plugin artifact version.
- Modify `gradle-plugin/build.gradle.kts`: expand the generated resource from
  `project.version` during `processResources`.
- Modify `gradle-plugin/src/main/kotlin/com/github/donglua/layoutx2c/plugin/LayoutX2CPlugin.kt`: load the generated version and remove the `VERSION` literal.
- Modify `gradle-plugin/src/test/kotlin/com/github/donglua/layoutx2c/plugin/LayoutX2CPluginTest.kt`: verify plugin version metadata and defaults.
- Modify `gradle-plugin/src/test/kotlin/com/github/donglua/layoutx2c/plugin/LayoutX2CPluginFunctionalTest.kt`: assert injected runtime and processor coordinates use the resolved version.

### Consumer Smoke

- Create `integration-tests/consumer-smoke/settings.gradle.kts`: resolve the
  isolated repository and external Android/plugin repositories without
  `includeBuild`.
- Create `integration-tests/consumer-smoke/build.gradle.kts`: define the
  standalone root and module inclusion.
- Create `integration-tests/consumer-smoke/gradle.properties`: set AndroidX,
  Kotlin, and Gradle memory defaults that are independent from the product
  build.
- Create `integration-tests/consumer-smoke/app/build.gradle.kts`: apply the
  published plugin by version, compile the minimal app, and define a smoke
  verification task for generated sources and reports.
- Create `integration-tests/consumer-smoke/app/src/main/AndroidManifest.xml`.
- Create `integration-tests/consumer-smoke/app/src/main/kotlin/com/example/consumer/LayoutX2CConfig.kt`.
- Create `integration-tests/consumer-smoke/app/src/main/res/layout/consumer_screen.xml`.
- Create `integration-tests/consumer-smoke/app/src/main/res/values/strings.xml`.
- Create `integration-tests/consumer-smoke/app/src/main/res/values/colors.xml`.

### Release Validation and CI

- Create `buildSrc/build.gradle.kts` and `buildSrc/src/main/kotlin/com/github/donglua/layoutx2c/build/ReleaseValidation.kt` for pure version and Markdown link validation.
- Create `buildSrc/src/test/kotlin/com/github/donglua/layoutx2c/build/ReleaseValidationTest.kt` for those helpers.
- Create `scripts/verify-maven-release.sh`: retry Maven Central and plugin
  marker resolution for a published version.
- Modify `.github/workflows/check.yml`: run the local consumer smoke gate and
  retain Kover/Codecov uploads.
- Modify `.github/workflows/release.yml`: check out the requested tag for
  manual dispatch, run `releaseCheck` before secrets, verify published
  coordinates, and create a GitHub Release after verification.

### Documentation

- Create `CHANGELOG.md`: index stable release notes and migration documents.
- Create `docs/releases/1.0.0.md`: archive the 1.0.0 notes from the release tag.
- Move `docs/RELEASE_NOTES_1_1.md` to `docs/releases/1.1.0.md`.
- Move `docs/RELEASE_NOTES_1_2_0.md` to `docs/releases/1.2.0.md`.
- Move `docs/RELEASE_NOTES_1_3_0.md` to `docs/releases/1.3.0.md`.
- Create `docs/releases/1.4.0.md`: document the published multi-module,
  runtime-extension, and synthetic-attribute changes.
- Create `docs/releases/1.4.1.md`: hold the next candidate's release notes.
- Create `docs/migrations/1.0.md`: restore the stable API and compatibility
  boundary as a versioned migration document.
- Create compatibility stubs at the former `docs/RELEASE_NOTES_*.md` paths
  pointing to the new files so existing repository links remain useful.
- Modify `README.md`: show the latest stable `1.4.0` plugin version, add the
  report command and tested-toolchain table near the setup path, and link the
  release/migration documents.
- Modify `docs/RELEASE.md`: use `${VERSION}` consistently, reference
  `docs/releases/${VERSION}.md`, and document `releaseCheck` and the post-
  publication verification script.
- Modify `docs/ROADMAP.md`: preserve the current `1.4.0` published status,
  remove stale 1.0 external-release wording, and distinguish tested API 23-36
  from future compatibility claims.

## Task 1: Centralize the Artifact Version

**Files:**

- Modify: `build.gradle.kts`
- Modify: `demo/build.gradle.kts`
- Modify: `runtime/build.gradle.kts`
- Create: `gradle-plugin/src/main/resources/com/github/donglua/layoutx2c/plugin/layoutx2c-version.properties`
- Modify: `gradle-plugin/build.gradle.kts`
- Modify: `gradle-plugin/src/main/kotlin/com/github/donglua/layoutx2c/plugin/LayoutX2CPlugin.kt`
- Modify: `gradle-plugin/src/test/kotlin/com/github/donglua/layoutx2c/plugin/LayoutX2CPluginTest.kt`
- Test: `gradle-plugin/src/test/kotlin/com/github/donglua/layoutx2c/plugin/LayoutX2CPluginFunctionalTest.kt`

- [ ] **Step 1: Add failing version-provider assertions**

Extend `LayoutX2CPluginTest` with a test that calls the package-private
version loader and asserts the generated resource contains the current project
version. Extend the functional fixture with a task that inspects the actual
dependency graph:

```kotlin
tasks.register("assertLayoutX2CDependencyVersion") {
    doLast {
        val expected = providers.gradleProperty("expectedLayoutX2CVersion").get()
        val coordinates = configurations.getByName("implementation").dependencies
            .filter { it.group == "io.github.donglua.layoutx2c" }
            .associate { it.name to it.version }
        check(coordinates["runtime"] == expected) {
            "runtime version ${coordinates["runtime"]} != $expected"
        }
        val processor = configurations.getByName("ksp").dependencies
            .single { it.name == "ksp-processor" }
        check(processor.version == expected) {
            "processor version ${processor.version} != $expected"
        }
    }
}
```

The test fixture must pass `-PexpectedLayoutX2CVersion=1.4.1-SNAPSHOT` and run
`:app:assertLayoutX2CDependencyVersion`. It must fail against the current
literal `1.4.0` plugin dependency.

- [ ] **Step 2: Run the targeted failing tests**

Run:

```bash
./gradlew :gradle-plugin:test --tests '*LayoutX2CPluginTest'
./gradlew :gradle-plugin:test --tests '*LayoutX2CPluginFunctionalTest'
```

Expected: the new assertion reports that the plugin injects `1.4.0` instead of
the requested `1.4.1-SNAPSHOT`.

- [ ] **Step 3: Define one root version provider**

Replace `extra["versionName"]`, `publishingVersion`, and the hard-coded default
with the following configuration in `build.gradle.kts`:

```kotlin
val versionProperty = providers.gradleProperty("layoutx2c.version").orNull
val versionEnvironment = providers.environmentVariable("VERSION").orNull
if (versionProperty != null && versionEnvironment != null && versionProperty != versionEnvironment) {
    throw GradleException(
        "Conflicting LayoutX2C versions: layoutx2c.version=$versionProperty, VERSION=$versionEnvironment"
    )
}

val publishingVersion = providers.gradleProperty("layoutx2c.version")
    .orElse(providers.environmentVariable("VERSION"))
    .orElse("1.4.1-SNAPSHOT")
```

Keep `GROUP` behavior unchanged. Assign every subproject `version` from
`publishingVersion`; use `rootProject.version.toString()` for the demo app
`defaultConfig.versionName`. Remove the now-unused `groupId` and `versionName`
delegates from `runtime/build.gradle.kts`.

- [ ] **Step 4: Generate plugin version metadata from the project version**

Create the resource template:

```properties
version=${layoutx2cVersion}
```

Configure `gradle-plugin/build.gradle.kts`:

```kotlin
import org.gradle.language.jvm.tasks.ProcessResources

tasks.named<ProcessResources>("processResources") {
    inputs.property("layoutx2cVersion", project.version.toString())
    filesMatching("com/github/donglua/layoutx2c/plugin/layoutx2c-version.properties") {
        expand("layoutx2cVersion" to project.version.toString())
    }
}
```

Replace `GROUP`/`VERSION` dependency construction in `LayoutX2CPlugin` with a
resource-backed loader:

```kotlin
private const val VERSION_RESOURCE =
    "/com/github/donglua/layoutx2c/plugin/layoutx2c-version.properties"

internal fun layoutX2CPluginVersion(): String {
    val properties = LayoutX2CPlugin::class.java.getResourceAsStream(VERSION_RESOURCE)
        ?.use { java.util.Properties().also { loaded -> loaded.load(it) } }
        ?: throw GradleException("LayoutX2C plugin version metadata is missing: $VERSION_RESOURCE")
    return properties.getProperty("version")
        ?.takeIf { it.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?")) }
        ?: throw GradleException("Invalid LayoutX2C plugin version metadata in $VERSION_RESOURCE")
}
```

Use `layoutX2CPluginVersion()` for both the runtime and `ksp-processor`
coordinates. Remove `LayoutX2CPlugin.VERSION`; no production dependency
injection path may contain another version literal.

- [ ] **Step 5: Make the targeted tests pass**

Run:

```bash
./gradlew :gradle-plugin:test --tests '*LayoutX2CPluginTest'
./gradlew :gradle-plugin:test --tests '*LayoutX2CPluginFunctionalTest'
./gradlew :gradle-plugin:processResources -Playoutx2c.version=1.4.1
```

Expected: tests pass and
`gradle-plugin/build/resources/main/com/github/donglua/layoutx2c/plugin/layoutx2c-version.properties`
contains `version=1.4.1`.

- [ ] **Step 6: Commit the version-provider change**

```bash
git add build.gradle.kts demo/build.gradle.kts runtime/build.gradle.kts \
  gradle-plugin/build.gradle.kts \
  gradle-plugin/src/main/resources \
  gradle-plugin/src/main/kotlin/com/github/donglua/layoutx2c/plugin/LayoutX2CPlugin.kt \
  gradle-plugin/src/test
git commit -m "build: centralize published artifact version"
```

## Task 2: Publish and Consume from an Isolated Maven Repository

**Files:**

- Modify: `build.gradle.kts`
- Create: `integration-tests/consumer-smoke/settings.gradle.kts`
- Create: `integration-tests/consumer-smoke/build.gradle.kts`
- Create: `integration-tests/consumer-smoke/gradle.properties`
- Create: `integration-tests/consumer-smoke/app/build.gradle.kts`
- Create: `integration-tests/consumer-smoke/app/src/main/AndroidManifest.xml`
- Create: `integration-tests/consumer-smoke/app/src/main/kotlin/com/example/consumer/LayoutX2CConfig.kt`
- Create: `integration-tests/consumer-smoke/app/src/main/res/layout/consumer_screen.xml`
- Create: `integration-tests/consumer-smoke/app/src/main/res/values/strings.xml`
- Create: `integration-tests/consumer-smoke/app/src/main/res/values/colors.xml`

- [ ] **Step 1: Add a local publication task that has no signing requirement**

Add a root repository directory and configure it for every Maven publication:

```kotlin
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec

val consumerSmokeRepository = layout.buildDirectory.dir("consumer-smoke/repository")

subprojects {
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension>("publishing") {
            repositories {
                maven {
                    name = "consumerSmoke"
                    url = consumerSmokeRepository.get().asFile.toURI()
                }
            }
        }
    }
}

val publishToConsumerSmokeRepository = tasks.register("publishToConsumerSmokeRepository") {
    group = "verification"
    description = "Publishes all LayoutX2C Maven publications to the consumer smoke repository."
}

val cleanConsumerSmokeRepository = tasks.register<Delete>("cleanConsumerSmokeRepository") {
    delete(consumerSmokeRepository)
}

gradle.projectsEvaluated {
    val publicationTasks = subprojects.flatMap { subproject ->
        subproject.tasks.matching { task ->
            task.name.startsWith("publish") && task.name.endsWith("ToConsumerSmokeRepository")
        }
    }
    publishToConsumerSmokeRepository.configure {
        dependsOn(cleanConsumerSmokeRepository, publicationTasks)
    }
}
```

Do not add the repository to `settings.gradle.kts`; it is an output repository
for the root build only. Ensure the task covers `runtime`, `compiler-core`,
`ksp-processor`, `gradle-plugin`, and both Gradle plugin marker publications.

- [ ] **Step 2: Create the standalone settings and root build**

Use the root-provided repository path and version, with no composite build:

```kotlin
import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        maven { url = uri(providers.gradleProperty("layoutx2c.consumerRepo").get()) }
        google()
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("io.github.donglua.layoutx2c") version providers.gradleProperty("layoutx2c.version").get()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri(providers.gradleProperty("layoutx2c.consumerRepo").get()) }
        google()
        mavenCentral()
    }
}

rootProject.name = "layoutx2c-consumer-smoke"
include(":app")
```

The root build file must be empty except for `plugins {}`-compatible Gradle
configuration, and `gradle.properties` must contain only stable AndroidX and
memory settings:

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
```

- [ ] **Step 3: Add the minimal published-coordinate consumer**

The app build must use the version and repository supplied by the root task. The
plugin version is declared in `settings.gradle.kts`'s `pluginManagement` block,
so the app build does not hard-code a product version:

```kotlin
plugins {
    id("com.android.application") version "9.3.1"
    id("org.jetbrains.kotlin.android") version "2.2.21"
    id("io.github.donglua.layoutx2c")
}

android {
    namespace = "com.example.consumer"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.consumer"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
}

layoutX2C {
    maxFallbackLayouts.set(0)
}

tasks.register("verifyLayoutX2CSmoke") {
    dependsOn("layoutX2CReport")
    doLast {
        check(file("build/generated/ksp/debug/kotlin/com/example/consumer/generated/LayoutX2CGenerated.kt").isFile)
        check(file("build/reports/layoutx2c/index.json").isFile)
        check(file("build/reports/layoutx2c/index.html").isFile)
    }
}
```

Add `@FastLayouts("consumer_screen")` in
`LayoutX2CConfig.kt`, a `TextView` layout with `wrap_content` dimensions and
`@string/consumer_title`, and matching string/color resources. Do not add
`includeBuild`, project dependencies, TestKit classpaths, or direct references
to the repository source tree.

- [ ] **Step 4: Add the root smoke execution task**

Register a root `consumerSmoke` task that depends on publication, then invokes
the standalone wrapper with the generated repository and version:

```kotlin
val consumerSmoke = tasks.register<Exec>("consumerSmoke") {
    dependsOn(publishToConsumerSmokeRepository)
    workingDir(layout.projectDirectory.dir("integration-tests/consumer-smoke"))
    commandLine(
        rootProject.file("gradlew").absolutePath,
        "verifyLayoutX2CSmoke",
        "--refresh-dependencies",
        "-Playoutx2c.consumerRepo=${consumerSmokeRepository.get().asFile.absolutePath}",
        "-Playoutx2c.version=${publishingVersion.get()}",
        "--no-configuration-cache",
        "--stacktrace"
    )
}
```

The nested build uses the root wrapper executable by absolute path but remains
independent of the root `settings.gradle.kts`; it must not use composite
substitution.

- [ ] **Step 5: Run the consumer smoke test**

Run:

```bash
./gradlew consumerSmoke -Playoutx2c.version=1.4.1-SNAPSHOT --no-daemon
```

Expected: the root publishes all required coordinates to
`build/consumer-smoke/repository`, the standalone build resolves the plugin
marker and transitive artifacts, and `verifyLayoutX2CSmoke` succeeds.

- [ ] **Step 6: Commit the consumer smoke project**

```bash
git add build.gradle.kts integration-tests/consumer-smoke
git commit -m "test: verify published coordinates with consumer smoke project"
```

## Task 3: Add Deterministic Release Validation

**Files:**

- Create: `buildSrc/build.gradle.kts`
- Create: `buildSrc/src/main/kotlin/com/github/donglua/layoutx2c/build/ReleaseValidation.kt`
- Create: `buildSrc/src/test/kotlin/com/github/donglua/layoutx2c/build/ReleaseValidationTest.kt`
- Modify: `build.gradle.kts`

- [ ] **Step 1: Write failing pure validation tests**

Create `buildSrc/build.gradle.kts` with only the Kotlin DSL and test
dependencies:

```kotlin
plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.5")
}
```

Add tests for the exact accepted version forms and Markdown behavior:

```kotlin
import java.io.File

@Test
fun `release version accepts stable and rc semver`() {
    assertThat(validateReleaseVersion("1.4.1")).isNull()
    assertThat(validateReleaseVersion("1.4.1-rc.1")).isNull()
}

@Test
fun `release version rejects snapshot and malformed values`() {
    assertThat(validateReleaseVersion("1.4.1-SNAPSHOT")).contains("stable SemVer")
    assertThat(validateReleaseVersion("1.4")).contains("stable SemVer")
}

@Test
fun `markdown validator ignores external links and checks local targets`() {
    val markdown = "[local](docs/RELEASE.md) [web](https://example.com) [anchor](#section)"
    assertThat(findBrokenLocalLinks(markdown, repoRoot.resolve("README.md"), repoRoot)).isEmpty()
}

@Test
fun `markdown validator reports missing local targets`() {
    val markdown = "[missing](docs/missing.md)"
    assertThat(findBrokenLocalLinks(markdown, repoRoot.resolve("README.md"), repoRoot))
        .containsExactly("docs/missing.md")
}
```

Add a failure assertion for `docs/RELEASE.md` linking to a nonexistent local
file. The tests must fail before the helper implementation exists.

- [ ] **Step 2: Run buildSrc tests to confirm the failure**

Run:

```bash
./gradlew :buildSrc:test --stacktrace
```

Expected: compilation fails because the validation helpers are not defined.

- [ ] **Step 3: Implement the pure validation helpers**

`ReleaseValidation.kt` must expose these exact functions:

```kotlin
fun validateReleaseVersion(version: String): String?

fun findBrokenLocalLinks(markdown: String, document: File, repoRoot: File): List<String>
```

Use one SemVer regex that accepts `major.minor.patch` with an optional
pre-release suffix, rejects `SNAPSHOT`, and returns a human-readable error
instead of throwing. Parse Markdown destinations with a regex that accepts
`[label](target)`, strips optional angle brackets and anchors, skips `http`,
`https`, `mailto`, and `#` destinations, and resolves remaining paths relative
to the document's parent directory while rejecting paths outside `repoRoot`.
Do not recurse into arbitrary directories.

Add `ReleaseValidationTest` cases for Windows-style separators, missing files,
external URLs, anchors, and a path containing spaces.

- [ ] **Step 4: Add publication metadata validation**

In `build.gradle.kts`, add a task that inspects
`build/consumer-smoke/repository` after `publishToConsumerSmokeRepository`:

```kotlin
val requiredArtifacts = listOf("runtime", "compiler-core", "ksp-processor", "gradle-plugin")
requiredArtifacts.forEach { artifact ->
    val pom = repositoryDir.resolve(
        "io/github/donglua/layoutx2c/$artifact/$version/$artifact-$version.pom"
    )
    check(pom.isFile) { "Missing published POM: $pom" }
    check(pom.readText().contains("<groupId>io.github.donglua.layoutx2c</groupId>"))
    check(pom.readText().contains("<version>$version</version>"))
}
```

Also verify the plugin marker at
`io/github/donglua/layoutx2c/io.github.donglua.layoutx2c.gradle.plugin/<version>/`.
The validator must report the exact missing path.

Import `findBrokenLocalLinks` from `buildSrc`, then define the two root-build
validation helpers used by `releaseCheck`:

```kotlin
import com.github.donglua.layoutx2c.build.findBrokenLocalLinks

fun validateRepositoryPomFiles(version: String) {
    val repositoryDir = consumerSmokeRepository.get().asFile
    val artifacts = listOf("runtime", "compiler-core", "ksp-processor", "gradle-plugin")
    artifacts.forEach { artifact ->
        val pom = repositoryDir.resolve(
            "io/github/donglua/layoutx2c/$artifact/$version/$artifact-$version.pom"
        )
        check(pom.isFile) { "Missing published POM: $pom" }
        val content = pom.readText()
        check("<groupId>io.github.donglua.layoutx2c</groupId>" in content)
        check("<version>$version</version>" in content)
    }
    val markerDir = repositoryDir.resolve(
        "io/github/donglua/layoutx2c/io.github.donglua.layoutx2c.gradle.plugin/$version"
    )
    check(markerDir.listFiles()?.any { it.extension == "pom" } == true) {
        "Missing Gradle plugin marker under: $markerDir"
    }
}

fun validateDocumentationLinks() {
    val markdownFiles = sequenceOf(file("README.md"), file("CHANGELOG.md")) +
        file("docs").walkTopDown().filter { it.isFile && it.extension == "md" }
    markdownFiles.forEach { document ->
        val broken = findBrokenLocalLinks(document.readText(), document, rootDir)
        check(broken.isEmpty()) { "Broken links in $document: ${broken.joinToString()}" }
    }
}
```

- [ ] **Step 5: Register `releaseCheck`**

Register a `validateReleaseCandidate` task for all cheap checks, and make every
expensive prerequisite depend on it. This makes invalid versions and broken
documentation fail before tests, Android assembly, or publication. Then
register a root lifecycle task with these dependencies and checks:

```kotlin
val validateReleaseCandidate = tasks.register("validateReleaseCandidate") {
    group = "verification"
    doLast {
        val version = publishingVersion.get()
        check(validateReleaseVersion(version) == null) {
            "releaseCheck requires stable SemVer, received $version"
        }
        check(version == providers.gradleProperty("layoutx2c.version").orNull ||
            version == providers.environmentVariable("VERSION").orNull) {
            "releaseCheck requires an explicit candidate version"
        }
        if (providers.environmentVariable("GITHUB_EVENT_NAME").orNull == "push") {
            providers.environmentVariable("GITHUB_REF_NAME").orNull?.let { refName ->
                check(refName == version) {
                    "Release tag $refName does not match candidate version $version"
                }
            }
        }
        validateDocumentationLinks()
    }
}

val releaseCheck = tasks.register("releaseCheck") {
    group = "verification"
    description = "Validates a LayoutX2C release candidate without remote publication."
    dependsOn(validateReleaseCandidate)
    dependsOn(
        "test",
        "koverXmlReport",
        "koverHtmlReport",
        ":demo:assembleDebug",
        ":runtime:assembleRelease",
        ":demo:assembleRelease",
        ":demo:assembleDebugAndroidTest",
        "consumerSmoke"
    )
    doLast {
        val version = publishingVersion.get()
        validateRepositoryPomFiles(version)
    }
}

listOf(
    "test",
    "koverXmlReport",
    "koverHtmlReport",
    ":demo:assembleDebug",
    ":runtime:assembleRelease",
    ":demo:assembleRelease",
    ":demo:assembleDebugAndroidTest",
    "consumerSmoke"
).forEach { taskPath ->
    tasks.matching { it.path == taskPath }.configureEach {
        dependsOn(validateReleaseCandidate)
    }
}
```

The task must fail for the default `1.4.1-SNAPSHOT` and pass for an explicit
candidate. On GitHub Actions, additionally compare `GITHUB_REF_NAME` with the
effective version when the workflow is a tag release. On a local branch without
a tag, skip only that tag comparison; do not skip version, metadata, docs, or
consumer checks.

- [ ] **Step 6: Run release validation**

Run the pure and integration gates:

```bash
./gradlew :buildSrc:test
./gradlew releaseCheck -Playoutx2c.version=1.4.1 --no-configuration-cache --no-daemon
```

Expected: the first command passes; the second runs the full JVM/Android/local
publication/consumer gate and leaves no remote publication side effects.

- [ ] **Step 7: Commit the release validation**

```bash
git add buildSrc build.gradle.kts
git commit -m "build: add deterministic release readiness checks"
```

## Task 4: Normalize Release History and Adoption Documentation

**Files:**

- Create: `CHANGELOG.md`
- Create: `docs/releases/1.0.0.md`
- Move: `docs/RELEASE_NOTES_1_1.md` to `docs/releases/1.1.0.md`
- Move: `docs/RELEASE_NOTES_1_2_0.md` to `docs/releases/1.2.0.md`
- Move: `docs/RELEASE_NOTES_1_3_0.md` to `docs/releases/1.3.0.md`
- Create: `docs/releases/1.4.0.md`
- Create: `docs/migrations/1.0.md`
- Create: `docs/RELEASE_NOTES_1_1.md`
- Create: `docs/RELEASE_NOTES_1_2_0.md`
- Create: `docs/RELEASE_NOTES_1_3_0.md`
- Modify: `README.md`
- Modify: `docs/RELEASE.md`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Move release notes and add compatibility stubs**

Use `git mv` for the three existing notes, then make each old path a short
redirect document. For example:

```markdown
# Release Notes Moved

The release notes for LayoutX2C 1.1.0 are now maintained at
[`docs/releases/1.1.0.md`](releases/1.1.0.md).
```

Preserve all Chinese and English release content in the new locations. The
redirect stubs prevent repository-relative links from silently breaking while
the versioned directory becomes the canonical source.

- [ ] **Step 2: Add 1.0 migration and 1.4 release history**

Copy the stable API boundary from the historical `1.0.0` tag into
`docs/migrations/1.0.md`, including `@PublicApi`, `@ExperimentalApi`, cache
behavior, compatibility contract, and breaking-change policy.

Write `docs/releases/1.4.0.md` with these verified highlights:

- generated registries discovered across application and library modules via
  `ServiceLoader`, with reflection fallback for older generated output;
- custom `ResourceLoader` and `ViewFactory` extension points;
- synthetic `AttributeSet` support for custom View factories and typed custom
  attributes;
- Android Gradle Plugin 9.3.1 and Gradle parallel-sync configuration;
- multi-module functional test coverage.

Do not claim generated-vs-inflated device equivalence for paths only covered by
JVM/TestKit tests.

Create `docs/releases/1.4.1.md` as the candidate notes file used by the release
workflow. It must describe the version-provider, published-coordinate smoke,
release gate, and documentation improvements delivered by this plan. The
workflow uses the stable base version for release candidates:

```bash
notes_version="${RELEASE_VERSION%%-*}"
notes_file="docs/releases/$notes_version.md"
```

- [ ] **Step 3: Add the changelog index**

Create `CHANGELOG.md` with links only:

```markdown
# Changelog

- [1.4.0](docs/releases/1.4.0.md) - Runtime extension hooks, multi-module registry discovery, and synthetic attributes.
- [1.3.0](docs/releases/1.3.0.md) - Custom View factory extension point.
- [1.2.0](docs/releases/1.2.0.md) - Resource loader extension point.
- [1.1.0](docs/releases/1.1.0.md) - Safe attribute, DataBinding, and digest improvements.
- [1.0.0](docs/releases/1.0.0.md) - First stable release.

See [the 1.0 migration guide](docs/migrations/1.0.md) for the stable API boundary.
```

- [ ] **Step 4: Make README adoption-first**

Update the first setup block to show the latest stable plugin version:

```kotlin
plugins {
    id("io.github.donglua.layoutx2c") version "1.4.0"
}
```

Update manual coordinates to `1.4.0`, add the report command immediately after
the first configuration example, and add a compact compatibility table:

```markdown
| Item | Current repository verification |
| --- | --- |
| Gradle / JDK | Gradle 9.5.1 / JDK 21 |
| Android toolchain | AGP 9.3.1, compileSdk 36, minSdk 23 |
| Kotlin / KSP | Kotlin 2.2.21 / KSP 2.3.8 |
| Runtime API | Android API 23+ is the configured minimum |
```

Label this table as the current CI/toolchain baseline, not a claim that every
AGP or Android API combination is supported. Link to CHANGELOG, migration,
benchmarks, report output, and release instructions. Fix the existing spacing
typo in the opening paragraph while editing the section.

- [ ] **Step 5: Repair release guide and roadmap status**

In `docs/RELEASE.md`, replace hard-coded `1.1.0` examples with `${VERSION}`
where the command is generic, use `VERSION=1.4.1` only in the candidate example,
reference `docs/releases/${VERSION}.md`, and add:

```bash
./gradlew releaseCheck -Playoutx2c.version="$VERSION" --no-configuration-cache
./scripts/verify-maven-release.sh "$VERSION"
```

In `docs/ROADMAP.md`, keep `1.4.0` as published, remove references implying
1.0 publication is still pending, and add the release-adoption loop to `Next`:
single version ownership, local published-coordinate smoke test, releaseCheck,
and post-publication resolution verification. Keep the DataBinding and report
precision work already listed after this release-engineering item.

- [ ] **Step 6: Run documentation validation**

Run:

```bash
./gradlew :buildSrc:test
rg -n '1\.1\.0|RELEASE_NOTES_1_' docs/RELEASE.md docs/ROADMAP.md README.md CHANGELOG.md
git diff --check
```

Expected: only compatibility stubs and historical release notes contain the
old `RELEASE_NOTES_` paths; the release guide and roadmap contain no stale
hard-coded `1.1.0` release commands.

- [ ] **Step 7: Commit documentation changes**

```bash
git add CHANGELOG.md README.md docs
git commit -m "docs: normalize release history and adoption path"
```

## Task 5: Enforce the Gate in CI and Verify Published Artifacts

**Files:**

- Create: `scripts/verify-maven-release.sh`
- Modify: `.github/workflows/check.yml`
- Modify: `.github/workflows/release.yml`
- Modify: `docs/RELEASE.md`

- [ ] **Step 1: Add the Maven Central verification script**

Create an executable script with bounded retries:

```bash
#!/usr/bin/env bash
set -euo pipefail

version="${1:?usage: verify-maven-release.sh VERSION}"
base="https://repo1.maven.org/maven2/io/github/donglua/layoutx2c"

artifacts=(runtime compiler-core ksp-processor gradle-plugin)
for artifact in "${artifacts[@]}"; do
  url="$base/$artifact/$version/$artifact-$version.pom"
  curl --fail --silent --show-error --retry 8 --retry-delay 15 --retry-all-errors "$url" >/dev/null
done

marker="io.github.donglua.layoutx2c.gradle.plugin"
marker_url="$base/io/github/donglua/layoutx2c/$marker/$version/$marker-$version.pom"
curl --fail --silent --show-error --retry 8 --retry-delay 15 --retry-all-errors "$marker_url" >/dev/null
```

Use `chmod +x scripts/verify-maven-release.sh`. The script checks Maven Central
only; Plugin Portal verification remains the `publishPlugins` task result plus
the generated marker resolution in a subsequent consumer build.

- [ ] **Step 2: Run consumer smoke in normal CI**

Add after the Java/Gradle setup and before coverage upload:

```yaml
- name: Verify published-coordinate consumer
  run: ./gradlew consumerSmoke -Playoutx2c.version=1.4.1-SNAPSHOT --no-daemon
```

Use the same repository cache configured by `setup-gradle`, but keep
`--refresh-dependencies` inside the nested smoke build. Upload the smoke report
directory as an artifact when the task completes.

- [ ] **Step 3: Gate release workflow before credentials**

Change manual checkout to the requested tag and add a repository gate before
secret validation:

```yaml
- name: Check out
  uses: actions/checkout@v4
  with:
    ref: ${{ github.event_name == 'workflow_dispatch' && inputs.version || github.ref }}

- name: Verify repository release gate
  run: ./gradlew releaseCheck -Playoutx2c.version="$RELEASE_VERSION" --no-configuration-cache --no-daemon
```

The checkout step must fail if a manual version does not resolve to a tag. The
gate runs before GPG import and before Maven/Plugin Portal credentials are
validated.

- [ ] **Step 4: Verify published coordinates and create GitHub Release**

Change release job permissions to `contents: write`. After Maven publication,
run:

```yaml
- name: Verify Maven Central artifacts
  run: ./scripts/verify-maven-release.sh "$RELEASE_VERSION"

- name: Create GitHub Release
  env:
    GH_TOKEN: ${{ github.token }}
  run: |
    flags=()
    if [[ "$RELEASE_VERSION" == *-* ]]; then flags+=(--prerelease); fi
    gh release create "$RELEASE_VERSION" \
      --verify-tag \
      --title "LayoutX2C $RELEASE_VERSION" \
      --notes-file "docs/releases/${RELEASE_VERSION%%-*}.md" \
      "${flags[@]}"
```

Keep Plugin Portal publication optional as today. If it is skipped, the GitHub
Release notes must say so through the existing workflow log; Maven artifacts
remain the required release output. Do not add rollback or tag deletion logic.

- [ ] **Step 5: Validate workflows locally**

Run the repository's local YAML parser and shell syntax checks, then run:

```bash
ruby -e 'require "yaml"; ARGV.each { |path| YAML.load_file(path); puts "valid: #{path}" }' \
  .github/workflows/check.yml .github/workflows/release.yml
bash -n scripts/verify-maven-release.sh
./gradlew releaseCheck -Playoutx2c.version=1.4.1 --no-configuration-cache --no-daemon
```

Expected: shell syntax passes, the local release gate passes, and no workflow
step attempts remote publication without explicit tag/secret execution.

- [ ] **Step 6: Commit CI and release automation**

```bash
git add .github/workflows/check.yml .github/workflows/release.yml \
  scripts/verify-maven-release.sh docs/RELEASE.md
git commit -m "ci: enforce release readiness and artifact verification"
```

## Task 6: Full Verification and Handoff

**Files:**

- No source changes expected unless a preceding verification exposes a defect.

- [ ] **Step 1: Run the focused checks**

```bash
./gradlew :buildSrc:test
./gradlew :gradle-plugin:test
./gradlew consumerSmoke -Playoutx2c.version=1.4.1-SNAPSHOT --no-daemon
```

- [ ] **Step 2: Run the complete repository gate**

```bash
./gradlew releaseCheck -Playoutx2c.version=1.4.1 --no-configuration-cache --no-daemon
```

Expected: all JVM tests, Kover reports, demo/runtime assemblies, Android test
APK assembly, publication metadata checks, documentation checks, and the
standalone consumer smoke project pass.

- [ ] **Step 3: Inspect generated artifacts and working tree**

```bash
test -f build/reports/kover/report.xml
test -f build/consumer-smoke/repository/io/github/donglua/layoutx2c/runtime/1.4.1/runtime-1.4.1.pom
test -f integration-tests/consumer-smoke/app/build/reports/layoutx2c/index.json
git diff --check
git status --short --branch
```

Expected: all files exist, `git diff --check` is silent, and only intentional
commits are present.

- [ ] **Step 4: Commit any verification-only documentation correction**

If verification changes documentation wording, commit it separately:

```bash
git add README.md docs CHANGELOG.md
git commit -m "docs: align release verification instructions"
```

Do not tag or publish `1.4.1` as part of this plan. The final handoff reports
the exact release-check command and leaves tag creation to a separate explicit
maintainer action.

## Self-Review Checklist

- Version source: Task 1 removes root and plugin version drift and updates demo
  and runtime consumers of the old extra.
- Published-coordinate adoption: Task 2 avoids `includeBuild`, project
  dependencies, and TestKit plugin classpaths.
- Release gate: Task 3 validates SemVer, candidate overrides, tag context,
  POMs, plugin markers, documentation links, tests, assemblies, and smoke.
- Documentation: Task 4 covers current stable `1.4.0`, historical 1.0-1.3
  notes, migration policy, README setup, roadmap status, and compatibility
  wording.
- CI/release: Task 5 gates before secrets, retries remote Maven checks, and
  creates GitHub Releases only after publication verification.
- No placeholders: every task names exact files, commands, expected outcomes,
  and commit messages.
- Non-goals preserved: no new View support, no DataBinding expansion, no
  generator refactor, no automatic tag/publish action.
