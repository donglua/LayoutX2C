package com.github.donglua.layoutx2c.resources

import java.io.DataInputStream
import java.io.File
import java.util.jar.JarFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * A module-local Android resource reference such as @color/primary.
 */
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

/**
 * Resolves which generated R class owns a resource reference.
 */
interface ResourceReferenceResolver {
    fun resolve(type: String, name: String): ResolvedResourceReference?
}

/**
 * Legacy resolver used by tests and code paths that do not have symbol data.
 * It assumes every known-looking resource belongs to the generated file's
 * imported current-module R class.
 */
object PermissiveResourceReferenceResolver : ResourceReferenceResolver {
    override fun resolve(type: String, name: String): ResolvedResourceReference {
        return ResolvedResourceReference(packageName = null)
    }
}

/**
 * Symbol-backed resolver that keeps generated code on the correct R class when
 * layouts reference resources merged from dependency packages.
 */
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

/**
 * Returns the Kotlin expression for a resolved resource, using the imported R
 * for current-module references and a fully qualified package R otherwise.
 */
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

/**
 * Parses ordinary resource references that can be statically owned by an R
 * class. New id declarations and android framework resources are intentionally
 * excluded because they are handled by different codegen paths.
 */
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

/**
 * Compact resource symbol table assembled from source res folders, AGP symbol
 * files, or compiled R classes. The optional owners map records package names
 * for symbols that do not belong to the current module.
 */
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

    fun stableKey(): String {
        return (references + owners.keys)
            .sortedWith(compareBy<ResourceReference> { it.type }.thenBy { it.name })
            .joinToString(separator = "\n") { reference ->
                "${reference.type}/${reference.name}=${owners[reference].orEmpty()}"
            }
    }

    companion object {
        private val valueResourceTypes = setOf(
            "attr",
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

        fun fromRClassJar(jarFile: File): ResourceSymbolTable {
            if (!jarFile.isFile) return ResourceSymbolTable(emptySet())

            val symbols = linkedSetOf<ResourceReference>()
            val owners = linkedMapOf<ResourceReference, String>()

            runCatching {
                JarFile(jarFile).use { jar ->
                    jar.entries().asSequence()
                        .filter { !it.isDirectory && it.name.endsWith(".class") }
                        .forEach { entry ->
                            val rClass = parseRClassEntry(entry.name) ?: return@forEach
                            if (rClass.type == "styleable") return@forEach
                            val fieldNames = jar.getInputStream(entry).use { input ->
                                readClassFieldNames(DataInputStream(input))
                            }
                            fieldNames.forEach { fieldName ->
                                addSymbol(
                                    symbols = symbols,
                                    owners = owners,
                                    type = rClass.type,
                                    name = fieldName,
                                    owner = rClass.packageName
                                )
                            }
                        }
                }
            }

            return ResourceSymbolTable(symbols, owners)
        }

        fun fromRClassJars(jarFiles: Iterable<File>): ResourceSymbolTable {
            return jarFiles.fold(ResourceSymbolTable(emptySet())) { acc, file ->
                acc + fromRClassJar(file)
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

        private fun parseRClassEntry(entryName: String): RClassEntry? {
            if (!entryName.endsWith(".class")) return null
            val className = entryName.removeSuffix(".class")
            val marker = "/R$"
            val markerIndex = className.lastIndexOf(marker)
            if (markerIndex <= 0) return null
            val packageName = className.substring(0, markerIndex).replace('/', '.')
            val type = className.substring(markerIndex + marker.length)
            if (type.isBlank() || "$" in type) return null
            return RClassEntry(packageName = packageName, type = type)
        }

        private fun readClassFieldNames(input: DataInputStream): List<String> {
            if (input.readInt() != 0xCAFEBABE.toInt()) return emptyList()
            input.readUnsignedShort() // minor
            input.readUnsignedShort() // major

            val constantPool = arrayOfNulls<String>(input.readUnsignedShort())
            var index = 1
            while (index < constantPool.size) {
                when (input.readUnsignedByte()) {
                    1 -> constantPool[index] = input.readUTF()
                    3, 4 -> input.skipFully(4)
                    5, 6 -> {
                        input.skipFully(8)
                        index++
                    }
                    7, 8, 16, 19, 20 -> input.skipFully(2)
                    9, 10, 11, 12, 17, 18 -> input.skipFully(4)
                    15 -> input.skipFully(3)
                    else -> return emptyList()
                }
                index++
            }

            input.skipFully(6) // access_flags, this_class, super_class
            repeat(input.readUnsignedShort()) {
                input.skipFully(2)
            }

            val fieldNames = mutableListOf<String>()
            repeat(input.readUnsignedShort()) {
                input.readUnsignedShort() // access_flags
                val name = constantPool[input.readUnsignedShort()]
                input.readUnsignedShort() // descriptor_index
                repeat(input.readUnsignedShort()) {
                    input.skipFully(2) // attribute_name_index
                    val length = input.readInt()
                    input.skipFully(length)
                }
                if (name != null) {
                    fieldNames += name
                }
            }
            return fieldNames
        }

        private fun DataInputStream.skipFully(byteCount: Int) {
            var remaining = byteCount
            while (remaining > 0) {
                val skipped = skipBytes(remaining)
                if (skipped <= 0) {
                    throw java.io.EOFException("Unable to skip $byteCount bytes")
                }
                remaining -= skipped
            }
        }
    }
}

private data class RClassEntry(
    val packageName: String,
    val type: String
)
