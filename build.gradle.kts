import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

buildscript {
    extra["kotlinVersion"] = "2.2.21"
    extra["agpVersion"] = "9.2.1"
    extra["kspVersion"] = "2.3.8"
    extra["minSdk"] = 23
    extra["targetSdk"] = 36
    extra["compileSdk"] = 36
    extra["groupId"] = "com.github.donglua.layoutx2c"
    extra["versionName"] = "0.3.4"
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

subprojects {
    group = publishingGroup.get()
    version = publishingVersion.get()

    if (enableMavenCentralPublishing) {
        pluginManager.apply("com.vanniktech.maven.publish")
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

plugins.withId("com.vanniktech.maven.publish") {
    configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
        publishToMavenCentral()
        signAllPublications()
        coordinates(
            groupId = rootProject.extra["groupId"] as String,
            artifactId = project.name,
            version = rootProject.extra["versionName"] as String
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
