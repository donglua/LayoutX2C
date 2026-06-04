import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.bundling.Zip
import org.gradle.plugins.signing.SigningExtension

buildscript {
    extra["kotlinVersion"] = "2.2.21"
    extra["agpVersion"] = "9.2.1"
    extra["kspVersion"] = "2.3.8"
    extra["kotlinPoetVersion"] = "2.3.0"
    extra["minSdk"] = 23
    extra["targetSdk"] = 36
    extra["compileSdk"] = 36
    extra["groupId"] = "io.github.donglua.layoutx2c"
    extra["versionName"] = "1.0.0-rc.1"
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
val publishingVersion = providers.environmentVariable("VERSION")
    .orElse(rootProject.extra["versionName"] as String)
val enableMavenCentralPublishing = providers.gradleProperty("layoutx2c.enablePublishing").isPresent
val useGpgSigning = providers.gradleProperty("layoutx2c.useGpgSigning")
    .map(String::toBoolean)
    .orElse(false)
val centralPortalBundleRepositoryName = "centralPortalBundle"
val centralPortalStagingDir = layout.buildDirectory.dir("central-portal/staging")

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
}
