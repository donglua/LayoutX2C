package com.github.donglua.layoutx2c.ksp

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AgpResourceSymbolLocatorTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `infers module project directory from source set res directory`() {
        val projectDir = tempDir.newFolder("app")
        val resDir = projectDir.resolve("src/internal/res").apply { mkdirs() }

        val inferred = AgpResourceSymbolLocator.inferProjectDir(resDir)

        assertThat(inferred).isEqualTo(projectDir)
    }

    @Test
    fun `finds AGP resource symbol files under known intermediates`() {
        val projectDir = tempDir.newFolder("app")
        val runtimeSymbols = projectDir
            .resolve("build/intermediates/runtime_symbol_list/debug/processDebugResources/R.txt")
            .apply {
                parentFile.mkdirs()
                writeText("int color dependency_divider 0x7f060001")
            }
        val packageAwareSymbols = projectDir
            .resolve("build/intermediates/symbol_list_with_package_name/debug/generateDebugRFile/package-aware-r.txt")
            .apply {
                parentFile.mkdirs()
                writeText("com.example\ncolor package_divider")
            }
        projectDir
            .resolve("build/intermediates/other/debug/ignored/R.txt")
            .apply {
                parentFile.mkdirs()
                writeText("int color ignored 0x7f060002")
            }

        val files = AgpResourceSymbolLocator.findSymbolFiles(projectDir)

        assertThat(files).containsExactly(runtimeSymbols, packageAwareSymbols)
            .inOrder()
    }

    @Test
    fun `finds R class jars from Gradle root when starting at app module`() {
        val rootDir = tempDir.newFolder("root")
        rootDir.resolve("settings.gradle").writeText("include ':app', ':base'")
        val appDir = rootDir.resolve("app").apply { mkdirs() }
        val baseRJar = rootDir
            .resolve("base/build/intermediates/compile_r_class_jar/debug/generateDebugRFile/R.jar")
            .apply {
                parentFile.mkdirs()
                writeText("jar")
            }
        val appRuntimeRJar = rootDir
            .resolve("app/build/intermediates/compile_and_runtime_r_class_jar/debug/processDebugResources/R.jar")
            .apply {
                parentFile.mkdirs()
                writeText("jar")
            }

        val files = AgpResourceSymbolLocator.findRClassJars(appDir)

        assertThat(files).containsAtLeast(baseRJar, appRuntimeRJar)
    }
}
