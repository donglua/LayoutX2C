package com.github.donglua.layoutx2c.resources

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

interface StyleResolver {
    fun expand(styleReference: String?): StyleExpansion
}

object NoStyleResolver : StyleResolver {
    override fun expand(styleReference: String?): StyleExpansion {
        return if (styleReference == null) {
            StyleExpansion.None
        } else {
            StyleExpansion.Unsupported("No style resolver configured")
        }
    }
}

sealed class StyleExpansion {
    object None : StyleExpansion()
    data class Expanded(val attributes: Map<String, String>) : StyleExpansion()
    data class Unsupported(val reason: String) : StyleExpansion()
}

class StyleResourceRepository private constructor(
    private val baseDefinitions: Map<String, StyleDefinition>,
    private val qualifierStyleNames: Set<String>
) : StyleResolver {

    override fun expand(styleReference: String?): StyleExpansion {
        if (styleReference == null) return StyleExpansion.None
        val styleName = styleNameFromReference(styleReference)
            ?: return StyleExpansion.Unsupported("Unsupported style reference: $styleReference")
        return resolve(styleName, linkedSetOf())
    }

    private fun resolve(styleName: String, visiting: MutableSet<String>): StyleExpansion {
        if (styleName in qualifierStyleNames) {
            return StyleExpansion.Unsupported("Style $styleName has qualifier variants")
        }
        if (!visiting.add(styleName)) {
            return StyleExpansion.Unsupported("Style parent cycle at $styleName")
        }

        val definition = baseDefinitions[styleName]
            ?: return StyleExpansion.Unsupported("Style $styleName not found in current module values")

        val parentAttributes = when (val parentName = parentStyleName(definition.parent)) {
            null -> emptyMap()
            "" -> return StyleExpansion.Unsupported("Unsupported style parent: ${definition.parent}")
            else -> when (val parent = resolve(parentName, visiting)) {
                is StyleExpansion.Expanded -> parent.attributes
                is StyleExpansion.Unsupported -> return parent
                StyleExpansion.None -> emptyMap()
            }
        }
        visiting.remove(styleName)
        return StyleExpansion.Expanded(parentAttributes + definition.items)
    }

    private fun styleNameFromReference(reference: String): String? {
        return when {
            reference.startsWith("@style/") -> reference.removePrefix("@style/").takeIf { it.isNotBlank() }
            else -> null
        }
    }

    private fun parentStyleName(parent: String?): String? {
        val trimmed = parent?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            trimmed.startsWith("@style/") -> trimmed.removePrefix("@style/").takeIf { it.isNotBlank() }
            trimmed.startsWith("@android:style/") -> ""
            trimmed.startsWith("@") -> ""
            trimmed.startsWith("?") -> ""
            else -> trimmed
        }
    }

    private data class StyleDefinition(
        val name: String,
        val parent: String?,
        val items: Map<String, String>
    )

    companion object {
        fun fromResDir(resDir: File): StyleResourceRepository {
            val baseDefinitions = linkedMapOf<String, StyleDefinition>()
            val qualifierStyleNames = linkedSetOf<String>()

            resDir.listFiles()
                .orEmpty()
                .filter { it.isDirectory && it.name.startsWith("values") }
                .forEach { valuesDir ->
                    val target = if (valuesDir.name == "values") baseDefinitions else null
                    val qualifierTarget = if (valuesDir.name == "values") null else qualifierStyleNames
                    valuesDir.listFiles()
                        .orEmpty()
                        .filter { it.isFile && it.extension == "xml" }
                        .forEach { valuesFile ->
                            parseStylesFile(valuesFile).forEach { definition ->
                                if (target != null) {
                                    target[definition.name] = definition
                                } else {
                                    qualifierTarget?.add(definition.name)
                                }
                            }
                        }
                }

            return StyleResourceRepository(baseDefinitions, qualifierStyleNames)
        }

        private fun parseStylesFile(valuesFile: File): List<StyleDefinition> {
            val document = runCatching {
                DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(valuesFile)
            }.getOrNull() ?: return emptyList()

            val root = document.documentElement ?: return emptyList()
            val definitions = mutableListOf<StyleDefinition>()
            val children = root.childNodes
            for (index in 0 until children.length) {
                val element = children.item(index)
                if (element.nodeType != Node.ELEMENT_NODE || element.nodeName != "style") continue
                val style = element as Element
                val name = style.getAttribute("name").takeIf { it.isNotBlank() } ?: continue
                val parent = style.getAttribute("parent").takeIf { it.isNotBlank() }
                definitions += StyleDefinition(
                    name = name,
                    parent = parent,
                    items = styleItems(style)
                )
            }
            return definitions
        }

        private fun styleItems(style: Element): Map<String, String> {
            val items = linkedMapOf<String, String>()
            val children = style.childNodes
            for (index in 0 until children.length) {
                val element = children.item(index)
                if (element.nodeType != Node.ELEMENT_NODE || element.nodeName != "item") continue
                val item = element as Element
                val name = item.getAttribute("name").takeIf { it.isNotBlank() } ?: continue
                items[name] = item.textContent.trim()
            }
            return items
        }
    }
}
