package com.github.donglua.layoutx2c.runtime

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

class PackagePathConsistencyTest {

    @Test
    fun `kotlin source paths match package declarations`() {
        val moduleDir = runtimeModuleDir()
        val mismatches = listOf("src/main/java", "src/test/java")
            .flatMap { sourceSet ->
                val sourceRoot = File(moduleDir, sourceSet)
                assertWithMessage("source root exists: $sourceSet")
                    .that(sourceRoot.isDirectory)
                    .isTrue()
                sourceRoot.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .mapNotNull { file ->
                        val packageName = file.useLines { lines ->
                            lines.firstOrNull { it.startsWith("package ") }
                                ?.removePrefix("package ")
                                ?.trim()
                        } ?: return@mapNotNull null
                        val expectedPath = packageName.replace('.', File.separatorChar)
                        val parentPath = file.parentFile!!.relativeTo(sourceRoot).path
                        if (parentPath == expectedPath) {
                            null
                        } else {
                            "${file.relativeTo(moduleDir).path}: package $packageName expects $expectedPath"
                        }
                    }
                    .toList()
            }

        assertWithMessage("Kotlin source files should live under their declared package paths")
            .that(mismatches)
            .isEmpty()
    }

    @Test
    fun `runtime source marks 1_0 public api surface`() {
        val moduleDir = runtimeModuleDir()
        val sourceRoot = File(moduleDir, "src/main/java")
        val publicApiFile = File(
            sourceRoot,
            "com/github/donglua/layoutx2c/runtime/annotation/PublicApi.kt"
        )
        val experimentalApiFile = File(
            sourceRoot,
            "com/github/donglua/layoutx2c/runtime/annotation/ExperimentalApi.kt"
        )
        assertWithMessage("PublicApi annotation should exist")
            .that(publicApiFile.isFile)
            .isTrue()
        assertWithMessage("ExperimentalApi annotation should exist")
            .that(experimentalApiFile.isFile)
            .isTrue()
        assertWithMessage("PublicApi should be binary retained")
            .that(publicApiFile.readText())
            .contains("@Retention(AnnotationRetention.BINARY)")
        assertWithMessage("ExperimentalApi should be binary retained")
            .that(experimentalApiFile.readText())
            .contains("@Retention(AnnotationRetention.BINARY)")

        val publicApiFiles = listOf(
            "com/github/donglua/layoutx2c/runtime/LayoutFactory.kt",
            "com/github/donglua/layoutx2c/runtime/LayoutX2CRegistry.kt",
            "com/github/donglua/layoutx2c/runtime/LayoutX2CFactory2.kt",
            "com/github/donglua/layoutx2c/runtime/FallbackInflater.kt",
            "com/github/donglua/layoutx2c/runtime/annotation/FastLayoutConfig.kt",
            "com/github/donglua/layoutx2c/runtime/annotation/FastLayouts.kt",
            "com/github/donglua/layoutx2c/runtime/annotation/FastLayoutPattern.kt"
        )

        val missingMarkers = publicApiFiles
            .map { File(sourceRoot, it) }
            .filterNot { file -> file.readText().contains("@PublicApi") }
            .map { it.relativeTo(moduleDir).invariantSeparatorsPath }

        assertWithMessage("runtime 1.0 public API files should be marked @PublicApi")
            .that(missingMarkers)
            .isEmpty()
    }

    private fun runtimeModuleDir(): File {
        val moduleDir = listOf(File("."), File("runtime"))
            .map { it.canonicalFile }
            .first { File(it, "src/main/java").isDirectory }
        return moduleDir
    }
}
