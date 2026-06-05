package com.github.donglua.layoutx2c.ksp

import java.io.File
import java.security.MessageDigest
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

internal object ResourceDependencyScanner {

    data class Dependency(
        val key: String,
        val file: File?,
        val content: String
    )

    private data class ResourceRef(
        val type: String,
        val name: String
    ) {
        val key: String = "$type/$name"
    }

    private data class ValueDefinition(
        val key: String,
        val file: File,
        val content: String
    )

    private data class Capture(
        val key: String,
        val file: File,
        val startDepth: Int,
        val content: StringBuilder = StringBuilder()
    )

    fun scan(layoutFile: File, resDir: File): Set<Dependency> {
        val values = indexValues(resDir)
        val dependencies = linkedSetOf<Dependency>()
        val seenRefs = linkedSetOf<String>()

        layoutFiles(layoutFile, resDir)
            .flatMap { scanRefsInXmlAttributes(it) }
            .forEach { ref ->
                resolve(ref, resDir, values, dependencies, seenRefs)
            }

        return dependencies
    }

    private fun layoutFiles(layoutFile: File, resDir: File): List<File> {
        return listOf(layoutFile) + LayoutDependencyScanner.scanDependencies(layoutFile, resDir)
    }

    private fun resolve(
        ref: ResourceRef,
        resDir: File,
        values: Map<String, List<ValueDefinition>>,
        dependencies: MutableSet<Dependency>,
        seenRefs: MutableSet<String>
    ) {
        if (ref.type == "id") return
        if (!seenRefs.add(ref.key)) return

        val valueDefinitions = values[ref.key].orEmpty()
        if (valueDefinitions.isNotEmpty()) {
            valueDefinitions.forEach { definition ->
                dependencies.add(
                    Dependency(
                        key = definition.key,
                        file = definition.file,
                        content = definition.content
                    )
                )
                extractResourceRefs(definition.content).forEach { nested ->
                    resolve(nested, resDir, values, dependencies, seenRefs)
                }
                extractStyleParentRefs(definition.content).forEach { parent ->
                    resolve(parent, resDir, values, dependencies, seenRefs)
                }
            }
            return
        }

        val fileResources = fileResources(ref, resDir)
        if (fileResources.isNotEmpty()) {
            fileResources.forEach { file ->
                dependencies.add(
                    Dependency(
                        key = ref.key,
                        file = file,
                        content = file.sha256Hex()
                    )
                )
                file.readTextOrNull()?.let { content ->
                    extractResourceRefs(content).forEach { nested ->
                        resolve(nested, resDir, values, dependencies, seenRefs)
                    }
                }
            }
            return
        }

        dependencies.add(
            Dependency(
                key = "unresolved:${ref.key}",
                file = null,
                content = "UNRESOLVED"
            )
        )
    }

    private fun indexValues(resDir: File): Map<String, List<ValueDefinition>> {
        return resDir.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values") }
            .flatMap { valuesDir ->
                valuesDir.listFiles()
                    .orEmpty()
                    .filter { it.isFile && it.extension == "xml" }
                    .flatMap { valuesFile -> parseValuesFile(valuesFile) }
            }
            .groupBy { it.key }
    }

    private fun parseValuesFile(valuesFile: File): List<ValueDefinition> {
        val definitions = mutableListOf<ValueDefinition>()
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
        }

        try {
            factory.newSAXParser().parse(
                valuesFile,
                object : DefaultHandler() {
                    private var depth = 0
                    private var capture: Capture? = null

                    override fun startElement(
                        uri: String?,
                        localName: String?,
                        qName: String?,
                        attributes: Attributes
                    ) {
                        val tagName = tagName(qName, localName)
                        if (depth == 1 && tagName != "resources" && capture == null) {
                            val key = resourceKey(tagName, attributes)
                            if (key != null) {
                                capture = Capture(key = key, file = valuesFile, startDepth = depth)
                            }
                        }
                        capture?.content?.appendStartTag(tagName, attributes)
                        depth++
                    }

                    override fun characters(ch: CharArray, start: Int, length: Int) {
                        capture?.content?.append(String(ch, start, length))
                    }

                    override fun endElement(uri: String?, localName: String?, qName: String?) {
                        val tagName = tagName(qName, localName)
                        capture?.content?.apply {
                            append("</").append(tagName).append(">")
                        }
                        depth--

                        val active = capture
                        if (active != null && depth == active.startDepth) {
                            definitions += ValueDefinition(
                                key = active.key,
                                file = active.file,
                                content = active.content.toString()
                            )
                            capture = null
                        }
                    }
                }
            )
        } catch (_: Exception) {
            // Full XML parsing reports malformed values. Digest scanning stays best-effort.
        }

        return definitions
    }

    private fun resourceKey(tagName: String, attributes: Attributes): String? {
        val name = attributes.getValue("name")?.takeIf { it.isNotBlank() } ?: return null
        val type = when (tagName) {
            "item" -> attributes.getValue("type")?.takeIf { it.isNotBlank() } ?: return null
            else -> tagName
        }
        return "$type/$name"
    }

    private fun scanRefsInXmlAttributes(file: File): Set<ResourceRef> {
        if (!file.isFile) return emptySet()

        val refs = linkedSetOf<ResourceRef>()
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
        }

        try {
            factory.newSAXParser().parse(
                file,
                object : DefaultHandler() {
                    override fun startElement(
                        uri: String?,
                        localName: String?,
                        qName: String?,
                        attributes: Attributes
                    ) {
                        for (index in 0 until attributes.length) {
                            extractResourceRefs(attributes.getValue(index)).forEach(refs::add)
                        }
                    }
                }
            )
        } catch (_: Exception) {
            // Full XML parsing reports malformed layouts. Digest scanning stays best-effort.
        }

        return refs
    }

    private fun extractResourceRefs(value: String): Set<ResourceRef> {
        return resourceRefRegex.findAll(value)
            .mapNotNull { match ->
                val type = match.groupValues[1]
                val name = match.groupValues[2]
                when {
                    type == "id" -> null
                    type.startsWith("android") -> null
                    else -> ResourceRef(type = type, name = name)
                }
            }
            .toSet()
    }

    private fun extractStyleParentRefs(value: String): Set<ResourceRef> {
        return styleParentRegex.findAll(value)
            .mapNotNull { match -> styleParentRef(match.groupValues[1]) }
            .toSet()
    }

    private fun styleParentRef(rawValue: String): ResourceRef? {
        val value = rawValue.trim()
        val name = when {
            value.startsWith("@style/") -> value.removePrefix("@style/")
            value.startsWith("@android:") -> return null
            value.startsWith("@") -> return null
            value.startsWith("?") -> return null
            value.isBlank() -> return null
            else -> value
        }
        return ResourceRef(type = "style", name = name)
    }

    private fun fileResources(ref: ResourceRef, resDir: File): List<File> {
        if (ref.type !in fileResourceTypes) return emptyList()
        return resDir.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.substringBefore("-") == ref.type }
            .flatMap { resourceDir ->
                resourceDir.listFiles()
                    .orEmpty()
                    .filter { it.isFile && it.nameWithoutExtension == ref.name }
            }
    }

    private fun StringBuilder.appendStartTag(tagName: String, attributes: Attributes) {
        append("<").append(tagName)
        (0 until attributes.length)
            .map { index -> attributes.getQName(index) to attributes.getValue(index) }
            .sortedBy { it.first }
            .forEach { (name, value) ->
                append(" ").append(name).append("=\"").append(value).append("\"")
            }
        append(">")
    }

    private fun tagName(qName: String?, localName: String?): String {
        return qName?.takeIf { it.isNotBlank() }
            ?: localName?.takeIf { it.isNotBlank() }
            ?: ""
    }

    private fun File.sha256Hex(): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(readBytes())
            .joinToString(separator = "") { "%02x".format(it) }
    }

    private fun File.readTextOrNull(): String? {
        return runCatching { readText() }.getOrNull()
    }

    private val resourceRefRegex = Regex("""@(?!(?:\+?id|android):?)([A-Za-z0-9_]+)/([A-Za-z0-9_.]+)""")
    private val styleParentRegex = Regex("""<style\b[^>]*\bparent="([^"]+)"""")
    private val fileResourceTypes = setOf("color", "drawable", "mipmap")
}
