pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

plugins {
    id("org.jetbrains.kotlinx.kover.aggregation") version "0.9.8"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LayoutX2C"

include(":runtime")
include(":compiler-core")
include(":ksp-processor")
include(":gradle-plugin")
include(":demo")

kover {
    enableCoverage()

    reports {
        includedProjects.addAll(
            ":runtime",
            ":compiler-core",
            ":ksp-processor",
            ":gradle-plugin"
        )
    }
}
