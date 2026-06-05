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
}

private val File.invariantSeparatorsPath: String
    get() = path.replace(File.separatorChar, '/')
