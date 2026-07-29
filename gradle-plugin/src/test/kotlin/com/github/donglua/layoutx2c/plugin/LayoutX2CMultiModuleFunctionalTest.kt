package com.github.donglua.layoutx2c.plugin

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.zip.ZipFile
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LayoutX2CMultiModuleFunctionalTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `app apk merges generated registry providers from app and library`() {
        val projectDir = createFixture()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(":app:assembleDebug", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertThat(result.task(":app:assembleDebug")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(
            projectDir.resolve(
                "app/build/generated/ksp/debug/kotlin/com/example/app/generated/LayoutX2CRegistryProvider.kt"
            ).isFile
        ).isTrue()
        assertThat(
            projectDir.resolve(
                "feature/build/generated/ksp/debug/kotlin/com/example/feature/generated/LayoutX2CRegistryProvider.kt"
            ).isFile
        ).isTrue()

        val apk = projectDir.resolve("app/build/outputs/apk/debug/app-debug.apk")
        val providers = ZipFile(apk).use { zip ->
            val entry = zip.getEntry(SERVICE_DESCRIPTOR_PATH)
            assertThat(entry).isNotNull()
            zip.getInputStream(entry).bufferedReader().useLines { lines ->
                lines.filter(String::isNotBlank).toSet()
            }
        }
        assertThat(providers).containsExactly(
            "com.example.app.generated.LayoutX2CRegistryProvider",
            "com.example.feature.generated.LayoutX2CRegistryProvider"
        )
    }

    private fun createFixture(): File {
        val projectDir = tempDir.newFolder("layoutx2c-multi-module")
        val repoRoot = File(System.getProperty("user.dir")).parentFile
        val repoPath = repoRoot.invariantSeparatorsPath
        repoRoot.resolve("local.properties")
            .takeIf(File::isFile)
            ?.copyTo(projectDir.resolve("local.properties"), overwrite = true)

        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }

            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                }
            }

            includeBuild("$repoPath") {
                dependencySubstitution {
                    substitute(module("io.github.donglua.layoutx2c:runtime")).using(project(":runtime"))
                    substitute(module("io.github.donglua.layoutx2c:ksp-processor")).using(project(":ksp-processor"))
                }
            }

            rootProject.name = "layoutx2c-multi-module"
            include(":app", ":feature")
            """.trimIndent()
        )
        projectDir.resolve("build.gradle.kts").writeText("")

        createAndroidModule(
            projectDir = projectDir,
            spec = AndroidModuleSpec(
                name = "app",
                pluginId = "com.android.application",
                namespace = "com.example.app",
                layoutName = "app_panel",
                androidConfig = """
                    defaultConfig {
                        applicationId = "com.example.app"
                        targetSdk = 36
                        versionCode = 1
                        versionName = "1.0"
                    }
                """.trimIndent(),
                dependencies = "implementation(project(\":feature\"))"
            )
        )
        createAndroidModule(
            projectDir = projectDir,
            spec = AndroidModuleSpec(
                name = "feature",
                pluginId = "com.android.library",
                namespace = "com.example.feature",
                layoutName = "feature_panel"
            )
        )
        return projectDir
    }

    private fun createAndroidModule(
        projectDir: File,
        spec: AndroidModuleSpec
    ) {
        val moduleDir = projectDir.resolve(spec.name)
        moduleDir.mkdirs()
        moduleDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("${spec.pluginId}") version "9.3.1"
                id("io.github.donglua.layoutx2c")
            }

            android {
                namespace = "${spec.namespace}"
                compileSdk = 36

                defaultConfig {
                    minSdk = 23
                }

                ${spec.androidConfig}
            }

            dependencies {
                implementation("androidx.appcompat:appcompat:1.7.1")
                implementation("androidx.constraintlayout:constraintlayout:2.1.4")
                ${spec.dependencies}
            }
            """.trimIndent()
        )
        moduleDir.resolve("src/main/AndroidManifest.xml").apply {
            parentFile.mkdirs()
            writeText("""<manifest xmlns:android="http://schemas.android.com/apk/res/android" />""")
        }
        moduleDir.resolve("src/main/kotlin/${spec.namespace.replace('.', '/')}/LayoutX2CConfig.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package ${spec.namespace}

                import com.github.donglua.layoutx2c.runtime.annotation.FastLayouts

                @FastLayouts("${spec.layoutName}")
                interface LayoutX2CConfig
                """.trimIndent()
            )
        }
        moduleDir.resolve("src/main/res/layout/${spec.layoutName}.xml").apply {
            parentFile.mkdirs()
            writeText(
                """
                <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="${spec.layoutName}" />
                """.trimIndent()
            )
        }
    }

    private data class AndroidModuleSpec(
        val name: String,
        val pluginId: String,
        val namespace: String,
        val layoutName: String,
        val androidConfig: String = "",
        val dependencies: String = ""
    )

    private companion object {
        const val SERVICE_DESCRIPTOR_PATH =
            "META-INF/services/com.github.donglua.layoutx2c.runtime.GeneratedLayoutRegistry"
    }
}
