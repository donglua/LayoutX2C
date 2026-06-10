package com.github.donglua.layoutx2c.ksp

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import javax.xml.parsers.SAXParserFactory

internal object LayoutDependencyScanner {

    data class Dependency(
        val layoutRef: String,
        val file: File?
    )

    fun scanDependencies(layoutFile: File, resDir: File): Set<File> {
        return scanDependencyGraph(layoutFile, resDir)
            .mapNotNullTo(linkedSetOf()) { it.file }
    }

    fun scanDependencyGraph(layoutFile: File, resDir: File): Set<Dependency> {
        val visited = linkedSetOf<File>()
        val dependencies = linkedMapOf<String, Dependency>()
        val entryFiles = resDir.resolveLayoutFiles(layoutFile.nameWithoutExtension)
            .ifEmpty { listOf(layoutFile).filter { it.isFile } }
        for (entryFile in entryFiles) {
            scanFile(entryFile, resDir, visited, dependencies)
        }
        entryFiles.forEach { dependencies.remove(it.canonicalPath) }
        return dependencies.values.toSet()
    }

    private fun scanFile(
        layoutFile: File,
        resDir: File,
        visited: MutableSet<File>,
        dependencies: MutableMap<String, Dependency>
    ) {
        val canonicalLayout = layoutFile.canonicalFile
        if (!visited.add(canonicalLayout)) return
        if (!layoutFile.isFile) return

        for (layoutRef in scanLayoutRefs(layoutFile)) {
            val dependencyFile = resolveLayoutFile(layoutRef, resDir, layoutFile)
            val key = dependencyFile?.canonicalPath ?: "missing:$layoutRef"
            dependencies.putIfAbsent(key, Dependency(layoutRef, dependencyFile))
            if (dependencyFile != null) {
                scanFile(dependencyFile, resDir, visited, dependencies)
            }
        }
    }

    private fun scanLayoutRefs(layoutFile: File): Set<String> {
        val refs = linkedSetOf<String>()
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
        }
        val parser = factory.newSAXParser()
        try {
            parser.parse(
                layoutFile,
                object : DefaultHandler() {
                    override fun startElement(
                        uri: String?,
                        localName: String?,
                        qName: String?,
                        attributes: Attributes
                    ) {
                        when (qName ?: localName) {
                            "include" -> attributes.getValue("layout")?.let { raw ->
                                extractLayoutRef(raw)?.let(refs::add)
                            }
                            "ViewStub", "android.view.ViewStub" -> attributes.getValue("android:layout")?.let { raw ->
                                extractLayoutRef(raw)?.let(refs::add)
                            }
                        }
                    }
                }
            )
        } catch (_: Exception) {
            // Digest scanning is best-effort. The full XML parser reports malformed layouts.
        }
        return refs
    }

    private fun resolveLayoutFile(layoutRef: String, resDir: File, fromLayoutFile: File): File? {
        val siblingVariant = fromLayoutFile.parentFile?.resolve("$layoutRef.xml")
        if (siblingVariant?.isFile == true) {
            return siblingVariant
        }
        return resDir.primaryLayoutFile(layoutRef)
    }

    private fun extractLayoutRef(raw: String): String? {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("@layout/")) trimmed.removePrefix("@layout/") else null
    }
}
