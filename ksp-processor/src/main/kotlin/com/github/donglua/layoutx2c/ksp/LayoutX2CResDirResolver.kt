package com.github.donglua.layoutx2c.ksp

import java.io.File

internal object LayoutX2CResDirResolver {

    fun inferMainResDir(sourceFile: File): File? {
        inferSourceSetResDir(sourceFile)?.let { return it }
        inferGeneratedSourceMainResDir(sourceFile)?.let { return it }
        inferNearestProjectMainResDir(sourceFile)?.let { return it }

        var current: File? = sourceFile.parentFile
        while (current != null) {
            val resDir = current.resolve("res")
            if (resDir.isDirectory) {
                return resDir
            }
            current = current.parentFile
        }
        return null
    }

    private fun inferNearestProjectMainResDir(sourceFile: File): File? {
        var current: File? = sourceFile.absoluteFile.normalize().parentFile
        while (current != null) {
            val mainRes = current.resolve("src/main/res")
            if (mainRes.isDirectory) {
                return mainRes
            }
            current = current.parentFile
        }
        return null
    }

    private fun inferGeneratedSourceMainResDir(sourceFile: File): File? {
        var current: File? = sourceFile.absoluteFile.normalize().parentFile
        while (current != null) {
            if (current.name == "build") {
                val mainRes = current.parentFile?.resolve("src/main/res")
                return mainRes?.takeIf { it.isDirectory }
            }
            current = current.parentFile
        }
        return null
    }

    private fun inferSourceSetResDir(sourceFile: File): File? {
        val sourcePath = sourceFile.absoluteFile.normalize().toPath()
        val segments = sourcePath.map { it.toString() }
        val srcIndex = segments.indexOfLast { it == "src" }
        if (srcIndex < 0 || srcIndex + 1 >= segments.size) return null

        val projectDir = sourcePath.root.resolve(sourcePath.subpath(0, srcIndex)).toFile()
        val sourceSet = segments[srcIndex + 1]
        val sourceSetRes = projectDir.resolve("src").resolve(sourceSet).resolve("res")
        if (sourceSetRes.isDirectory) {
            return sourceSetRes
        }

        val mainRes = projectDir.resolve("src/main/res")
        return mainRes.takeIf { it.isDirectory }
    }
}
