import org.gradle.api.publish.maven.MavenPublication

plugins {
    id("com.android.library")
    id("maven-publish")
}

val compileSdk: Int by rootProject.extra
val minSdk: Int by rootProject.extra
val enableMavenCentralPublishing = providers.gradleProperty("layoutx2c.enablePublishing").isPresent

android {
    namespace = "com.github.donglua.layoutx2c.runtime"
    compileSdk = rootProject.extra["compileSdk"] as Int

    defaultConfig {
        minSdk = rootProject.extra["minSdk"] as Int
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    if (!enableMavenCentralPublishing) {
        publishing {
            singleVariant("release") {
                withSourcesJar()
            }
        }
    }
}

if (!enableMavenCentralPublishing) {
    afterEvaluate {
        publishing {
            publications {
                register<MavenPublication>("release") {
                    from(components["release"])
                }
            }
        }
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.10.0")
    implementation("androidx.appcompat:appcompat:1.7.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.5")
}
