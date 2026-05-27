package com.github.donglua.layoutx2c.runtime

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

class PackagePathConsistencyTest {

    @Test
    fun `kotlin source paths match package declarations`() {
        val moduleDir = listOf(File("."), File("runtime"))
            .map { it.canonicalFile }
            .first { File(it, "src/main/java").isDirectory }
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
}
