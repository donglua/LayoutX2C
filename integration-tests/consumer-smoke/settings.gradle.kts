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
