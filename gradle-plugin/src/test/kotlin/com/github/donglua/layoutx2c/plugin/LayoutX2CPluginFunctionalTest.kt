package com.github.donglua.layoutx2c.plugin

import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties

class LayoutX2CPluginFunctionalTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `digest cache restores deleted outputs and invalidates referenced resources`() {
        val fixture = createAndroidFixture()

        fixture.runKsp()
        val firstSnapshot = fixture.generatedSnapshot()
        val firstManifest = fixture.digestManifest()
        val firstRegistry = fixture.registryFile.readText()
        val firstRegistryDigest = firstManifest.getProperty("registry")

        assertThat(firstSnapshot.keys).containsAtLeast(
            "kotlin/com/example/generated/DemoOneX2C.kt",
            "kotlin/com/example/generated/DemoTwoX2C.kt",
            "kotlin/com/example/generated/PatternAlphaX2C.kt",
            "kotlin/com/example/generated/PatternBetaX2C.kt",
            "kotlin/com/example/generated/Layout_DemoOne.kt",
            "kotlin/com/example/generated/Layout_DemoTwo.kt",
            "kotlin/com/example/generated/Layout_PatternAlpha.kt",
            "kotlin/com/example/generated/Layout_PatternBeta.kt",
            "kotlin/com/example/generated/LayoutX2CGenerated.kt",
            "resources/com/example/generated/demo_one_report.json",
            "resources/com/example/generated/demo_two_report.json",
            "resources/com/example/generated/pattern_alpha_report.json",
            "resources/com/example/generated/pattern_beta_report.json"
        )
        assertThat(registryEntries(firstRegistry)).containsExactly(
            "LayoutX2CRegistry.register(R.layout.demo_one, Layout_DemoOne())",
            "LayoutX2CRegistry.register(R.layout.demo_two, Layout_DemoTwo())",
            "LayoutX2CRegistry.register(R.layout.pattern_alpha, Layout_PatternAlpha())",
            "LayoutX2CRegistry.register(R.layout.pattern_beta, Layout_PatternBeta())"
        ).inOrder()

        fixture.generatedSourceDir.deleteRecursively()
        fixture.runKsp()

        assertThat(fixture.generatedSnapshot()).isEqualTo(firstSnapshot)
        assertThat(fixture.registryFile.readText()).isEqualTo(firstRegistry)

        fixture.layoutVariant("layout-land", "demo_one").writeText(layoutXml("First landscape changed"))
        fixture.runKsp()

        val afterVariantManifest = fixture.digestManifest()
        assertThat(afterVariantManifest.getProperty("registry"))
            .isEqualTo(firstRegistryDigest)
        assertThat(afterVariantManifest.getProperty("demo_one"))
            .isNotEqualTo(firstManifest.getProperty("demo_one"))
        assertThat(afterVariantManifest.getProperty("demo_two"))
            .isEqualTo(firstManifest.getProperty("demo_two"))
        assertThat(afterVariantManifest.getProperty("pattern_alpha"))
            .isEqualTo(firstManifest.getProperty("pattern_alpha"))
        assertThat(afterVariantManifest.getProperty("pattern_beta"))
            .isEqualTo(firstManifest.getProperty("pattern_beta"))

        fixture.layout("demo_one").writeText(layoutXml("First changed"))
        fixture.runKsp()

        val afterLayoutManifest = fixture.digestManifest()
        val afterLayoutSnapshot = fixture.generatedSnapshot()
        assertThat(afterLayoutManifest.getProperty("registry"))
            .isEqualTo(firstRegistryDigest)
        assertThat(afterLayoutManifest.getProperty("demo_one"))
            .isNotEqualTo(afterVariantManifest.getProperty("demo_one"))
        assertThat(afterLayoutManifest.getProperty("demo_two"))
            .isEqualTo(afterVariantManifest.getProperty("demo_two"))
        assertThat(afterLayoutManifest.getProperty("pattern_alpha"))
            .isEqualTo(afterVariantManifest.getProperty("pattern_alpha"))
        assertThat(afterLayoutManifest.getProperty("pattern_beta"))
            .isEqualTo(afterVariantManifest.getProperty("pattern_beta"))
        assertThat(afterLayoutSnapshot["kotlin/com/example/generated/Layout_DemoOne.kt"])
            .isNotEqualTo(firstSnapshot["kotlin/com/example/generated/Layout_DemoOne.kt"])
        assertThat(afterLayoutSnapshot["kotlin/com/example/generated/Layout_DemoTwo.kt"])
            .isEqualTo(firstSnapshot["kotlin/com/example/generated/Layout_DemoTwo.kt"])
        assertThat(afterLayoutSnapshot["kotlin/com/example/generated/Layout_PatternAlpha.kt"])
            .isEqualTo(firstSnapshot["kotlin/com/example/generated/Layout_PatternAlpha.kt"])
        assertThat(afterLayoutSnapshot["kotlin/com/example/generated/Layout_PatternBeta.kt"])
            .isEqualTo(firstSnapshot["kotlin/com/example/generated/Layout_PatternBeta.kt"])
        assertThat(fixture.registryFile.readText()).isEqualTo(firstRegistry)

        fixture.generatedSourceDir.deleteRecursively()
        fixture.runKsp()

        assertThat(fixture.generatedSnapshot()).isEqualTo(afterLayoutSnapshot)
        assertThat(fixture.registryFile.readText()).isEqualTo(firstRegistry)

        fixture.valuesFile.writeText(valuesXml("#00ff00"))
        fixture.runKsp()

        val afterValuesManifest = fixture.digestManifest()
        assertThat(afterValuesManifest.getProperty("demo_one"))
            .isEqualTo(afterLayoutManifest.getProperty("demo_one"))
        assertThat(afterValuesManifest.getProperty("demo_two"))
            .isNotEqualTo(afterLayoutManifest.getProperty("demo_two"))
        assertThat(afterValuesManifest.getProperty("pattern_alpha"))
            .isEqualTo(afterLayoutManifest.getProperty("pattern_alpha"))
        assertThat(afterValuesManifest.getProperty("pattern_beta"))
            .isEqualTo(afterLayoutManifest.getProperty("pattern_beta"))
        assertThat(fixture.cacheDir("demo_one", afterLayoutManifest.getProperty("demo_one")).exists()).isTrue()
        assertThat(fixture.cacheDir("demo_two", afterLayoutManifest.getProperty("demo_two")).exists()).isFalse()
        assertThat(fixture.cacheDir("pattern_alpha", afterLayoutManifest.getProperty("pattern_alpha")).exists()).isTrue()
        assertThat(fixture.cacheDir("pattern_beta", afterLayoutManifest.getProperty("pattern_beta")).exists()).isTrue()
        assertThat(fixture.registryFile.readText()).isEqualTo(firstRegistry)
    }

    @Test
    fun `layoutX2CReport aggregates generated layout reports`() {
        val fixture = createAndroidFixture()

        fixture.runReport()

        val json = fixture.reportJson.readText()
        assertThat(json).contains("\"totalLayouts\": 5")
        assertThat(json).contains("\"FULL\"")
        assertThat(json).contains("\"FALLBACK\"")
        assertThat(json).contains("\"DATA_BINDING_WRAPPER\"")
        assertThat(json).contains("\"malformed_binding\"")
        assertThat(json).contains("\"fallbackNodes\"")
        assertThat(json).contains("\"path\": \"[0]\"")

        val html = fixture.reportHtml.readText()
        assertThat(html).contains("LayoutX2C Report")
        assertThat(html).contains("demo_one")
        assertThat(html).contains("malformed_binding")
        assertThat(html).contains("[0]")
    }

    @Test
    fun `layoutX2CReport fails when CI fallback policy is exceeded`() {
        val fixture = createAndroidFixture(
            """
                maxFallbackLayouts.set(0)
                failOnFallbackReasons.add("DATA_BINDING_WRAPPER")
            """.trimIndent()
        )

        val result = fixture.runReportAndFail()

        assertThat(result.output).contains("LayoutX2C report policy failed")
        assertThat(result.output).contains("fallback layouts: 1 > 0")
        assertThat(result.output).contains("DATA_BINDING_WRAPPER")
        assertThat(fixture.reportJson.isFile).isTrue()
        assertThat(fixture.reportHtml.isFile).isTrue()
    }

    private fun createAndroidFixture(layoutX2CConfiguration: String = ""): AndroidFixture {
        val projectDir = tempDir.newFolder("layoutx2c-functional")
        val repoRoot = File(System.getProperty("user.dir")).parentFile
        val repoPath = repoRoot.invariantSeparatorsPath
        repoRoot.resolve("local.properties")
            .takeIf { it.isFile }
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

            rootProject.name = "layoutx2c-functional"
            include(":app")
            """.trimIndent()
        )
        projectDir.resolve("build.gradle.kts").writeText("")

        val appDir = projectDir.resolve("app")
        appDir.mkdirs()
        appDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application") version "9.2.1"
                id("io.github.donglua.layoutx2c")
            }

            android {
                namespace = "com.example"
                compileSdk = 36

                defaultConfig {
                    applicationId = "com.example"
                    minSdk = 23
                    targetSdk = 36
                    versionCode = 1
                    versionName = "1.0"
                }
            }

            layoutX2C {
                $layoutX2CConfiguration
            }
            """.trimIndent()
        )

        appDir.resolve("src/main/AndroidManifest.xml").apply {
            parentFile.mkdirs()
            writeText("""<manifest xmlns:android="http://schemas.android.com/apk/res/android" />""")
        }
        appDir.resolve("src/main/kotlin/com/example/LayoutX2CConfig.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example

                import com.github.donglua.layoutx2c.runtime.annotation.FastLayouts
                import com.github.donglua.layoutx2c.runtime.annotation.FastLayoutPattern

                @FastLayouts("demo_one", "demo_two", "malformed_binding")
                @FastLayoutPattern(rClass = R::class, layoutPrefix = "pattern_")
                interface LayoutX2CConfig
                """.trimIndent()
            )
        }

        appDir.resolve("src/main/res/layout").mkdirs()
        appDir.resolve("src/main/res/layout/demo_one.xml").writeText(layoutXml("First"))
        appDir.resolve("src/main/res/layout-land").mkdirs()
        appDir.resolve("src/main/res/layout-land/demo_one.xml").writeText(layoutXml("First landscape"))
        appDir.resolve("src/main/res/layout/demo_two.xml").writeText(
            """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Second"
                android:textColor="@color/accent" />
            """.trimIndent()
        )
        appDir.resolve("src/main/res/layout/malformed_binding.xml").writeText(
            """
            <layout xmlns:android="http://schemas.android.com/apk/res/android">
                <data />
            </layout>
            """.trimIndent()
        )
        appDir.resolve("src/main/res/layout/pattern_beta.xml").writeText(layoutXml("Pattern beta"))
        appDir.resolve("src/main/res/layout/pattern_alpha.xml").writeText(layoutXml("Pattern alpha"))
        appDir.resolve("src/main/res/values/colors.xml").apply {
            parentFile.mkdirs()
            writeText(valuesXml("#ff0000"))
        }

        return AndroidFixture(projectDir, appDir)
    }

    private fun registryEntries(registry: String): List<String> {
        return registry.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("LayoutX2CRegistry.register") }
            .toList()
    }

    private fun layoutXml(text: String): String {
        return """
            <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="$text" />
        """.trimIndent()
    }

    private fun valuesXml(accent: String): String {
        return """<resources><color name="accent">$accent</color></resources>"""
    }

    private data class AndroidFixture(
        val projectDir: File,
        val appDir: File
    ) {
        val generatedSourceDir: File
            get() = appDir.resolve("build/generated/ksp/debug/kotlin/com/example/generated")

        val generatedKspDir: File
            get() = appDir.resolve("build/generated/ksp/debug")

        val registryFile: File
            get() = generatedSourceDir.resolve("LayoutX2CGenerated.kt")

        val valuesFile: File
            get() = appDir.resolve("src/main/res/values/colors.xml")

        val reportJson: File
            get() = appDir.resolve("build/reports/layoutx2c/index.json")

        val reportHtml: File
            get() = appDir.resolve("build/reports/layoutx2c/index.html")

        fun layout(name: String): File = appDir.resolve("src/main/res/layout/$name.xml")

        fun layoutVariant(variantDir: String, name: String): File {
            return appDir.resolve("src/main/res/$variantDir/$name.xml")
        }

        fun cacheDir(layoutName: String, digest: String): File {
            return appDir.resolve("build/layoutx2c/ksp/generated/$layoutName/$digest")
        }

        fun runKsp() {
            val result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments(":app:kspDebugKotlin", "--stacktrace")
                .withPluginClasspath()
                .build()

            assertThat(result.task(":app:kspDebugKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(registryFile.isFile).isTrue()
        }

        fun runReport() {
            val result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments(":app:layoutX2CReport", "--stacktrace")
                .withPluginClasspath()
                .build()

            assertThat(result.task(":app:layoutX2CReport")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(reportJson.isFile).isTrue()
            assertThat(reportHtml.isFile).isTrue()
        }

        fun runReportAndFail() = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(":app:layoutX2CReport", "--stacktrace")
            .withPluginClasspath()
            .buildAndFail()

        fun generatedSnapshot(): Map<String, String> {
            return generatedKspDir.walkTopDown()
                .filter { it.isFile }
                .associate { it.relativeTo(generatedKspDir).invariantSeparatorsPath to it.readText() }
        }

        fun digestManifest(): Properties {
            val manifest = appDir.resolve("build/layoutx2c/ksp/layoutx2c-digests.properties")
            assertThat(manifest.isFile).isTrue()
            return Properties().apply {
                manifest.inputStream().use(::load)
            }
        }
    }
}
