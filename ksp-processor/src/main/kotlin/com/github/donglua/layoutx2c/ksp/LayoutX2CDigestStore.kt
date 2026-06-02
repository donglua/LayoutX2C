package com.github.donglua.layoutx2c.ksp

import java.io.File
import java.security.MessageDigest
import java.util.Properties

internal class LayoutX2CDigestStore(private val manifestFile: File) {

    private val previous = Properties()
    private val current = Properties()

    init {
        if (manifestFile.isFile) {
            manifestFile.inputStream().use(previous::load)
        }
    }

    fun isUnchanged(layoutName: String, digest: String): Boolean {
        return previous.getProperty(layoutName) == digest
    }

    fun record(layoutName: String, digest: String) {
        current.setProperty(layoutName, digest)
    }

    fun save() {
        manifestFile.parentFile?.mkdirs()
        pruneStaleCaches()
        manifestFile.outputStream().use { output ->
            current.store(output, "LayoutX2C incremental manifest")
        }
    }

    fun cachedFile(layoutName: String, digest: String, fileName: String, extensionName: String): File {
        return manifestFile.parentFile
            .resolve("generated")
            .resolve(layoutName)
            .resolve(digest)
            .resolve("$fileName.$extensionName")
    }

    private fun pruneStaleCaches() {
        val generatedDir = manifestFile.parentFile.resolve("generated")
        if (!generatedDir.isDirectory) return

        generatedDir.listFiles()
            ?.filter { it.isDirectory }
            ?.forEach { layoutDir ->
                val currentDigest = current.getProperty(layoutDir.name)
                if (currentDigest == null) {
                    layoutDir.deleteRecursively()
                } else {
                    layoutDir.listFiles()
                        ?.filter { it.isDirectory && it.name != currentDigest }
                        ?.forEach { it.deleteRecursively() }
                }
            }
    }
}

internal object LayoutX2CDigestCalculator {

    // Bump this when the digest inputs or hashing semantics change.
    private const val SCHEMA_VERSION = "v5"

    fun layoutDigest(
        layoutFile: File,
        resDir: File,
        packageName: String,
        rPackageName: String
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateString(SCHEMA_VERSION)
        digest.updateString(packageName)
        digest.updateString(rPackageName)
        digest.updateFile(layoutFile, resDir)

        LayoutDependencyScanner.scanDependencyGraph(layoutFile, resDir)
            .sortedWith(
                compareBy<LayoutDependencyScanner.Dependency> { it.layoutRef }
                    .thenBy { dependency ->
                        dependency.file?.relativeTo(resDir)?.invariantSeparatorsPath ?: ""
                    }
            )
            .forEach { dependency ->
                digest.updateString("layout-dependency")
                digest.updateString(dependency.layoutRef)
                val dependencyFile = dependency.file
                if (dependencyFile == null) {
                    digest.updateString("MISSING")
                } else {
                    digest.updateString("PRESENT")
                    digest.updateFile(dependencyFile, resDir)
                }
            }

        val valuesDir = File(resDir, "values")
        valuesDir.walkTopDown()
            .filter { it.isFile && it.extension == "xml" }
            .sortedBy { it.relativeTo(resDir).invariantSeparatorsPath }
            .forEach { valuesFile ->
                digest.updateFile(valuesFile, resDir)
            }

        resourceSymbolDirs(resDir)
            .flatMap { resourceDir ->
                resourceDir.walkTopDown().filter { it.isFile }.toList()
            }
            .sortedBy { it.relativeTo(resDir).invariantSeparatorsPath }
            .forEach { resourceFile ->
                digest.updateFile(resourceFile, resDir)
            }

        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    fun contentDigest(
        content: String,
        packageName: String,
        rPackageName: String
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateString(SCHEMA_VERSION)
        digest.updateString(packageName)
        digest.updateString(rPackageName)
        digest.updateString(content)
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    private fun MessageDigest.updateFile(file: File, resDir: File) {
        updateString(file.relativeTo(resDir).invariantSeparatorsPath)
        update(file.readBytes())
    }

    private fun MessageDigest.updateString(value: String) {
        update(value.toByteArray(Charsets.UTF_8))
        update(0)
    }

    private fun resourceSymbolDirs(resDir: File): List<File> {
        return resDir.listFiles()
            ?.filter { resourceDir ->
                resourceDir.isDirectory &&
                    resourceDir.name.substringBefore("-") in setOf("color", "drawable", "layout", "mipmap")
            }
            ?: emptyList()
    }
}
