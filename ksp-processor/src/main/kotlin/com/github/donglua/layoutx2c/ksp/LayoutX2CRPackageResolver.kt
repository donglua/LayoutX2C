package com.github.donglua.layoutx2c.ksp

import java.io.File

internal object LayoutX2CRPackageResolver {

    private val namespacePattern = Regex(
        """\bnamespace\s*(?:=)?\s*["']([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*)["']"""
    )

    fun inferAndroidNamespace(sourceFile: File): String? {
        val projectDir = inferProjectDir(sourceFile) ?: return null
        return sequenceOf(
            projectDir.resolve("build.gradle"),
            projectDir.resolve("build.gradle.kts")
        )
            .firstNotNullOfOrNull { file ->
                file.takeIf { it.isFile }
                    ?.readText()
                    ?.let { namespacePattern.find(it)?.groupValues?.get(1) }
            }
    }

    private fun inferProjectDir(sourceFile: File): File? {
        val sourcePath = sourceFile.absoluteFile.normalize().toPath()
        val segments = sourcePath.map { it.toString() }

        val srcIndex = segments.indexOfLast { it == "src" }
        if (srcIndex > 0) {
            return sourcePath.root.resolve(sourcePath.subpath(0, srcIndex)).toFile()
        }

        val buildIndex = segments.indexOfLast { it == "build" }
        if (buildIndex > 0) {
            return sourcePath.root.resolve(sourcePath.subpath(0, buildIndex)).toFile()
        }

        var current: File? = sourceFile.absoluteFile.normalize().parentFile
        while (current != null) {
            if (current.resolve("build.gradle").isFile || current.resolve("build.gradle.kts").isFile) {
                return current
            }
            current = current.parentFile
        }
        return null
    }
}
