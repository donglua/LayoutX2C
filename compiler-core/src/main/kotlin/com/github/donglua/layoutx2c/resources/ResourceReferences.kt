package com.github.donglua.layoutx2c.resources

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

data class ResourceReference(
    val type: String,
    val name: String
)

data class ResolvedResourceReference(
    /**
     * Null means "use the generated file's imported current-module R".
     */
    val packageName: String?
)

interface ResourceReferenceResolver {
    fun resolve(type: String, name: String): ResolvedResourceReference?
}

object PermissiveResourceReferenceResolver : ResourceReferenceResolver {
    override fun resolve(type: String, name: String): ResolvedResourceReference {
        return ResolvedResourceReference(packageName = null)
    }
}

class StaticResourceReferenceResolver(
    owners: Map<ResourceReference, String>,
    private val currentPackageName: String
) : ResourceReferenceResolver {

    private val ownerByReference = owners

    override fun resolve(type: String, name: String): ResolvedResourceReference? {
        val owner = ownerByReference[ResourceReference(type, name)] ?: return null
        return ResolvedResourceReference(packageName = owner)
    }

    companion object {
        fun currentModule(
            currentPackageName: String,
            symbols: ResourceSymbolTable
        ): StaticResourceReferenceResolver {
            return StaticResourceReferenceResolver(
                owners = symbols.references.associateWith { symbols.owners[it] ?: currentPackageName },
                currentPackageName = currentPackageName
            )
        }
    }
}

fun ResourceReferenceResolver.referenceCode(
    type: String,
    name: String,
    currentPackageName: String
): String? {
    val resolved = resolve(type, name) ?: return null
    val owner = resolved.packageName
    return if (owner == null || owner == currentPackageName) {
        "R.$type.$name"
    } else {
        "$owner.R.$type.$name"
    }
}

fun parseResourceReference(value: String): ResourceReference? {
    if (!value.startsWith("@") || value.startsWith("@+")) return null
    if (value.startsWith("@android:")) return null
    val body = value.removePrefix("@")
    val slash = body.indexOf('/')
    if (slash <= 0 || slash == body.lastIndex) return null
    return ResourceReference(
        type = body.substring(0, slash),
        name = body.substring(slash + 1)
    )
}

class ResourceSymbolTable(
    val references: Set<ResourceReference>,
    val owners: Map<ResourceReference, String> = emptyMap()
) {

    fun contains(type: String, name: String): Boolean {
        return ResourceReference(type, name) in references
    }

    operator fun plus(other: ResourceSymbolTable): ResourceSymbolTable {
        return ResourceSymbolTable(
            references = references + other.references,
            owners = owners + other.owners
        )
    }

    companion object {
        private val valueResourceTypes = setOf(
            "color",
            "dimen",
            "drawable",
            "string",
            "style"
        )
        private val fileResourceTypes = setOf(
            "color",
            "drawable",
            "layout",
            "mipmap"
        )
        private val rTextPattern = Regex("""^(?:int|int\[\])\s+([A-Za-z0-9_]+)\s+([A-Za-z0-9_]+)\b""")

        fun fromResDir(resDir: File): ResourceSymbolTable {
            if (!resDir.isDirectory) return ResourceSymbolTable(emptySet())

            val symbols = linkedSetOf<ResourceReference>()
            scanValueResources(resDir, symbols)
            scanFileResources(resDir, symbols)
            return ResourceSymbolTable(symbols)
        }

        fun fromSymbolFile(symbolFile: File): ResourceSymbolTable {
            if (!symbolFile.isFile) return ResourceSymbolTable(emptySet())

            val symbols = linkedSetOf<ResourceReference>()
            val owners = linkedMapOf<ResourceReference, String>()
            var currentPackageName: String? = null

            symbolFile.forEachLine { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEachLine

                val rTextMatch = rTextPattern.find(line)
                if (rTextMatch != null) {
                    addSymbol(
                        symbols = symbols,
                        owners = owners,
                        type = rTextMatch.groupValues[1],
                        name = rTextMatch.groupValues[2],
                        owner = currentPackageName
                    )
                    return@forEachLine
                }

                val parts = line.split(Regex("""\s+"""))
                when {
                    parts.size == 1 && "." in parts[0] -> currentPackageName = parts[0]
                    parts.size >= 2 -> addSymbol(
                        symbols = symbols,
                        owners = owners,
                        type = parts[0],
                        name = parts[1],
                        owner = currentPackageName
                    )
                }
            }

            return ResourceSymbolTable(symbols, owners)
        }

        fun fromSymbolFiles(symbolFiles: Iterable<File>): ResourceSymbolTable {
            return symbolFiles.fold(ResourceSymbolTable(emptySet())) { acc, file ->
                acc + fromSymbolFile(file)
            }
        }

        private fun scanValueResources(resDir: File, symbols: MutableSet<ResourceReference>) {
            resDir.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith("values") }
                ?.flatMap { valuesDir ->
                    valuesDir.listFiles()
                        ?.filter { it.isFile && it.extension == "xml" }
                        ?: emptyList()
                }
                ?.forEach { valuesFile -> scanValuesFile(valuesFile, symbols) }
        }

        private fun scanValuesFile(valuesFile: File, symbols: MutableSet<ResourceReference>) {
            val document = runCatching {
                DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(valuesFile)
            }.getOrNull() ?: return

            val root = document.documentElement ?: return
            val children = root.childNodes
            for (index in 0 until children.length) {
                val element = children.item(index)
                val nodeName = element.nodeName
                val attrs = element.attributes ?: continue
                val nameAttr = attrs.getNamedItem("name")?.nodeValue ?: continue
                val type = if (nodeName == "item") {
                    attrs.getNamedItem("type")?.nodeValue
                } else {
                    nodeName
                }
                if (type != null && type in valueResourceTypes) {
                    symbols += ResourceReference(type = type, name = nameAttr)
                }
            }
        }

        private fun scanFileResources(resDir: File, symbols: MutableSet<ResourceReference>) {
            resDir.listFiles()
                ?.filter { it.isDirectory }
                ?.forEach { typedDir ->
                    val type = typedDir.name.substringBefore("-")
                    if (type !in fileResourceTypes) return@forEach
                    typedDir.listFiles()
                        ?.filter { it.isFile && !it.name.startsWith(".") }
                        ?.forEach { file ->
                            symbols += ResourceReference(type = type, name = file.nameWithoutExtension)
                        }
                }
        }

        private fun addSymbol(
            symbols: MutableSet<ResourceReference>,
            owners: MutableMap<ResourceReference, String>,
            type: String,
            name: String,
            owner: String?
        ) {
            if (type == "styleable") return
            val reference = ResourceReference(type = type, name = name)
            symbols += reference
            if (owner != null) {
                owners[reference] = owner
            }
        }
    }
}
