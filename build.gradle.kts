import com.github.donglua.layoutx2c.build.findBrokenLocalLinks
import com.github.donglua.layoutx2c.build.validateReleaseVersion
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Zip
import org.gradle.plugins.signing.SigningExtension

buildscript {
    extra["kotlinVersion"] = "2.2.21"
    extra["agpVersion"] = "9.3.1"
    extra["kspVersion"] = "2.3.8"
    extra["kotlinPoetVersion"] = "2.3.0"
    extra["minSdk"] = 23
    extra["targetSdk"] = 36
    extra["compileSdk"] = 36
    extra["groupId"] = "io.github.donglua.layoutx2c"
}

plugins {
    id("com.android.application") version "${extra["agpVersion"]}" apply false
    id("com.android.library") version "${extra["agpVersion"]}" apply false
    id("org.jetbrains.kotlin.android") version "${extra["kotlinVersion"]}" apply false
    id("org.jetbrains.kotlin.jvm") version "${extra["kotlinVersion"]}" apply false
    id("com.google.devtools.ksp") version "${extra["kspVersion"]}" apply false
    id("com.vanniktech.maven.publish") version "0.36.0" apply false
    id("com.gradle.plugin-publish") version "2.1.1" apply false
}

val publishingGroup = providers.environmentVariable("GROUP")
    .orElse(rootProject.extra["groupId"] as String)
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
val enableMavenCentralPublishing = providers.gradleProperty("layoutx2c.enablePublishing").isPresent
val useGpgSigning = providers.gradleProperty("layoutx2c.useGpgSigning")
    .map(String::toBoolean)
    .orElse(false)
val centralPortalBundleRepositoryName = "centralPortalBundle"
val centralPortalStagingDir = layout.buildDirectory.dir("central-portal/staging")
val consumerSmokeRepositoryName = "consumerSmoke"
val consumerSmokeRepository = layout.buildDirectory.dir("consumer-smoke/repository")

version = publishingVersion.get()

val cleanConsumerSmokeRepository = tasks.register<Delete>("cleanConsumerSmokeRepository") {
    group = "verification"
    description = "Deletes the isolated consumer smoke Maven repository."
    delete(consumerSmokeRepository)
}

val publishToConsumerSmokeRepository = tasks.register("publishToConsumerSmokeRepository") {
    group = "verification"
    description = "Publishes all LayoutX2C Maven publications to the consumer smoke repository."
}

tasks.register<Exec>("consumerSmoke") {
    group = "verification"
    description = "Verifies a standalone Android project can consume the published LayoutX2C coordinates."
    dependsOn(publishToConsumerSmokeRepository)
    workingDir(layout.projectDirectory.dir("integration-tests/consumer-smoke"))
    commandLine(
        rootProject.file("gradlew").absolutePath,
        ":app:verifyLayoutX2CSmoke",
        "--refresh-dependencies",
        "-Playoutx2c.consumerRepo=${consumerSmokeRepository.get().asFile.absolutePath}",
        "-Playoutx2c.version=${publishingVersion.get()}",
        "--no-configuration-cache",
        "--stacktrace"
    )
}

fun validatePomCoordinates(pom: File, artifact: String, version: String) {
    check(pom.isFile) { "Missing published POM: $pom" }
    val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        isExpandEntityReferences = false
    }
    val root = factory.newDocumentBuilder().parse(pom).documentElement
    fun directChildText(name: String): String? = (0 until root.childNodes.length)
        .asSequence()
        .map { root.childNodes.item(it) }
        .filterIsInstance<org.w3c.dom.Element>()
        .firstOrNull { it.tagName == name }
        ?.textContent
        ?.trim()

    check(directChildText("groupId") == "io.github.donglua.layoutx2c") {
        "Unexpected groupId in published POM: $pom"
    }
    check(directChildText("artifactId") == artifact) {
        "Unexpected artifactId in published POM: $pom"
    }
    check(directChildText("version") == version) {
        "Unexpected version in published POM: $pom"
    }
}

fun validateRepositoryPomFiles(version: String) {
    val repositoryDir = consumerSmokeRepository.get().asFile
    listOf("runtime", "compiler-core", "ksp-processor", "gradle-plugin").forEach { artifact ->
        val pom = repositoryDir.resolve(
            "io/github/donglua/layoutx2c/$artifact/$version/$artifact-$version.pom"
        )
        validatePomCoordinates(pom, artifact, version)
    }

    val markerArtifact = "io.github.donglua.layoutx2c.gradle.plugin"
    val markerPom = repositoryDir.resolve(
        "io/github/donglua/layoutx2c/$markerArtifact/$version/$markerArtifact-$version.pom"
    )
    validatePomCoordinates(markerPom, markerArtifact, version)
}

fun validateDocumentationLinks() {
    val readme = file("README.md")
    check(readme.isFile) { "Missing documentation entry: README.md" }
    val entryDocuments = sequenceOf(readme) + sequenceOf(file("CHANGELOG.md")).filter { it.isFile }
    val internalPlanningDirectory = file("docs/superpowers")
    val documentation = file("docs").walkTopDown()
        .onEnter { directory -> directory != internalPlanningDirectory }
        .filter { it.isFile && it.extension == "md" }

    (entryDocuments + documentation).forEach { document ->
        val broken = findBrokenLocalLinks(document.readText(), document, rootDir)
        check(broken.isEmpty()) {
            "Broken links in ${document.relativeTo(rootDir)}: ${broken.joinToString()}"
        }
    }
}

val validateReleaseCandidate = tasks.register("validateReleaseCandidate") {
    group = "verification"
    description = "Validates release version, tag, and documentation inputs before expensive checks."
    doLast {
        val version = publishingVersion.get()
        check(validateReleaseVersion(version) == null) {
            "releaseCheck requires stable SemVer, received $version"
        }
        val explicitVersion = versionProperty ?: versionEnvironment
        check(explicitVersion != null && version == explicitVersion) {
            "releaseCheck requires an explicit candidate version"
        }

        val githubEvent = providers.environmentVariable("GITHUB_EVENT_NAME").orNull
        val githubRefType = providers.environmentVariable("GITHUB_REF_TYPE").orNull
        if (githubEvent == "push" && githubRefType == "tag") {
            val refName = providers.environmentVariable("GITHUB_REF_NAME").orNull
            check(refName == version) {
                "Release tag $refName does not match candidate version $version"
            }
        }
        validateDocumentationLinks()
    }
}

val releaseCheck = tasks.register("releaseCheck") {
    group = "verification"
    description = "Validates a LayoutX2C release candidate without remote publication."
    dependsOn(validateReleaseCandidate)
    doLast {
        validateRepositoryPomFiles(publishingVersion.get())
    }
}

allprojects {
    tasks.configureEach {
        if (path != validateReleaseCandidate.get().path) {
            mustRunAfter(validateReleaseCandidate)
        }
    }
}

val validateCentralPortalBundleInputs = tasks.register("validateCentralPortalBundleInputs") {
    group = "publishing"
    description = "Validates signing properties required for a Central Portal upload bundle."
    doLast {
        val missing = listOf("signingInMemoryKey", "signingInMemoryKeyPassword")
            .filterNot { providers.gradleProperty(it).isPresent }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing required Gradle properties for Central Portal bundle: ${missing.joinToString()}"
            )
        }
    }
}

val cleanCentralPortalBundle = tasks.register<Delete>("cleanCentralPortalBundle") {
    group = "publishing"
    description = "Deletes Central Portal bundle staging outputs."
    delete(layout.buildDirectory.dir("central-portal"))
}

val publishCentralPortalBundleComponents = tasks.register("publishCentralPortalBundleComponents") {
    group = "publishing"
    description = "Publishes all LayoutX2C components to a local Central Portal staging repository."
}

tasks.register<Zip>("buildCentralPortalBundle") {
    group = "publishing"
    description = "Builds a Maven Repository Layout zip for Sonatype Central Portal Publish Component."
    archiveFileName.set("layoutx2c-${publishingVersion.get()}-central-bundle.zip")
    destinationDirectory.set(layout.buildDirectory.dir("central-portal"))
    from(centralPortalStagingDir)
    dependsOn(publishCentralPortalBundleComponents)
}

subprojects {
    group = publishingGroup.get()
    version = publishingVersion.get()

    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension>("publishing") {
            repositories {
                maven {
                    name = consumerSmokeRepositoryName
                    url = consumerSmokeRepository.get().asFile.toURI()
                }
            }
        }
    }

    if (enableMavenCentralPublishing) {
        pluginManager.apply("com.vanniktech.maven.publish")
        plugins.withId("com.vanniktech.maven.publish") {
            configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
                publishToMavenCentral()
                if (providers.gradleProperty("signingInMemoryKey").isPresent || useGpgSigning.get()) {
                    signAllPublications()
                }
                coordinates(
                    groupId = publishingGroup.get(),
                    artifactId = project.name,
                    version = publishingVersion.get()
                )
                pom {
                    name.set("LayoutX2C ${project.name}")
                    description.set("Compile-time XML layout to Kotlin code generation for Android")
                    inceptionYear.set("2026")
                    url.set("https://github.com/donglua/LayoutX2C")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("donglua")
                            name.set("donglua")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/donglua/LayoutX2C.git")
                        developerConnection.set("scm:git:ssh://github.com/donglua/LayoutX2C.git")
                        url.set("https://github.com/donglua/LayoutX2C")
                    }
                }
            }
        }
        plugins.withId("maven-publish") {
            extensions.configure<PublishingExtension>("publishing") {
                repositories {
                    maven {
                        name = centralPortalBundleRepositoryName
                        url = centralPortalStagingDir.get().asFile.toURI()
                    }
                }
            }
        }
        plugins.withId("signing") {
            extensions.configure<SigningExtension>("signing") {
                if (useGpgSigning.get()) {
                    useGpgCmd()
                }
            }
        }
    } else if (name == "gradle-plugin") {
        pluginManager.withPlugin("java-gradle-plugin") {
            pluginManager.apply("maven-publish")
        }
    } else {
        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            pluginManager.apply("maven-publish")
            extensions.configure<PublishingExtension>("publishing") {
                publications {
                    register<MavenPublication>("maven") {
                        from(components["java"])
                    }
                }
            }
        }
    }
}

gradle.projectsEvaluated {
    val consumerSmokePublishTasks = subprojects.flatMap { subproject ->
        subproject.tasks.withType(PublishToMavenRepository::class.java)
            .matching { it.repository.name == consumerSmokeRepositoryName }
            .toList()
    }
    consumerSmokePublishTasks.forEach {
        it.dependsOn(cleanConsumerSmokeRepository)
    }
    publishToConsumerSmokeRepository.configure {
        dependsOn(consumerSmokePublishTasks)
    }

    val bundlePublishTasks = subprojects.flatMap { subproject ->
        subproject.tasks.withType(PublishToMavenRepository::class.java)
            .matching { it.repository.name == centralPortalBundleRepositoryName }
            .toList()
    }

    bundlePublishTasks.forEach {
        it.dependsOn(validateCentralPortalBundleInputs, cleanCentralPortalBundle)
    }

    publishCentralPortalBundleComponents.configure {
        dependsOn(bundlePublishTasks)
    }

    val releasePrerequisites = listOf(
        project(":runtime").tasks.named("test").get(),
        project(":compiler-core").tasks.named("test").get(),
        project(":ksp-processor").tasks.named("test").get(),
        project(":gradle-plugin").tasks.named("test").get(),
        project(":demo").tasks.named("test").get(),
        tasks.named("koverXmlReport").get(),
        tasks.named("koverHtmlReport").get(),
        project(":demo").tasks.named("assembleDebug").get(),
        project(":runtime").tasks.named("assembleRelease").get(),
        project(":demo").tasks.named("assembleRelease").get(),
        project(":demo").tasks.named("assembleDebugAndroidTest").get(),
        tasks.named("consumerSmoke").get()
    )
    releasePrerequisites.forEach { prerequisite ->
        prerequisite.dependsOn(validateReleaseCandidate)
    }
    releaseCheck.configure {
        dependsOn(releasePrerequisites)
    }
}
