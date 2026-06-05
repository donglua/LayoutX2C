package com.github.donglua.layoutx2c.ksp

import java.io.File

internal object AgpResourceSymbolLocator {

    private val knownIntermediates = setOf(
        "runtime_symbol_list",
        "compile_symbol_list",
        "symbol_list_with_package_name",
        "local_only_symbol_list"
    )

    private val knownSymbolFileNames = setOf(
        "R.txt",
        "R-def.txt",
        "package-aware-r.txt"
    )
    private val knownRClassJarIntermediates = setOf(
        "compile_r_class_jar",
        "compile_and_runtime_r_class_jar"
    )

    fun inferProjectDir(resDir: File): File? {
        var current: File? = resDir.absoluteFile
        while (current != null) {
            if (current.name == "src") {
                return current.parentFile
            }
            current = current.parentFile
        }
        return null
    }

    fun findSymbolFiles(projectDir: File): List<File> {
        val intermediatesDir = projectDir.resolve("build/intermediates")
        if (!intermediatesDir.isDirectory) return emptyList()

        return knownIntermediates
            .map { intermediatesDir.resolve(it) }
            .filter { it.isDirectory }
            .flatMap { root ->
                root.walkTopDown()
                    .filter { it.isFile && it.name in knownSymbolFileNames }
                    .toList()
                    .sortedBy { it.invariantSeparatorsPath }
            }
    }

    fun findRClassJars(projectDir: File): List<File> {
        val searchRoots = listOfNotNull(projectDir, findGradleRoot(projectDir))
            .distinctBy { it.absolutePath }

        return searchRoots
            .flatMap { root ->
                root.walkTopDown()
                    .onEnter { dir -> dir.shouldEnterForRClassSearch() }
                    .filter { file ->
                        file.isFile &&
                            file.name == "R.jar" &&
                            file.invariantSeparatorsPath.contains("/build/intermediates/") &&
                            knownRClassJarIntermediates.any { intermediate ->
                                file.invariantSeparatorsPath.contains("/$intermediate/")
                            }
                    }
                    .toList()
            }
            .distinctBy { it.absolutePath }
            .sortedBy { it.invariantSeparatorsPath }
    }

    private fun findGradleRoot(projectDir: File): File? {
        var current: File? = projectDir.absoluteFile
        while (current != null) {
            if (current.resolve("settings.gradle").isFile ||
                current.resolve("settings.gradle.kts").isFile
            ) {
                return current
            }
            current = current.parentFile
        }
        return null
    }

    private fun File.shouldEnterForRClassSearch(): Boolean {
        return name !in setOf(
            ".git",
            ".gradle",
            ".idea",
            "build-cache",
            "build-cache-3"
        )
    }
}

private val File.invariantSeparatorsPath: String
    get() = path.replace(File.separatorChar, '/')
