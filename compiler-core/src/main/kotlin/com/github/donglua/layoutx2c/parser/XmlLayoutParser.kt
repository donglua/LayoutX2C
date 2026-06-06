package com.github.donglua.layoutx2c.parser

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 解析 Android layout XML 文件为 LayoutTree。
 *
 * @param includeResolver 可选的 include 解析器，用于递归解析 <include> 引用的布局文件。
 *                        为 null 时 include 节点将保持为普通节点。
 */
class XmlLayoutParser(
    private val includeResolver: IncludeResolver? = null
) {

    fun parse(xmlFile: File): LayoutTree {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(xmlFile)
        val rootElement = document.documentElement

        val parsedRoot = parseLayoutRoot(rootElement)
        return LayoutTree(
            root = parsedRoot.node,
            fileName = xmlFile.nameWithoutExtension,
            rootMetadata = parsedRoot.metadata
        )
    }

    fun parse(xmlContent: String, fileName: String): LayoutTree {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(xmlContent.byteInputStream())
        val rootElement = document.documentElement

        val parsedRoot = parseLayoutRoot(rootElement)
        return LayoutTree(
            root = parsedRoot.node,
            fileName = fileName,
            rootMetadata = parsedRoot.metadata
        )
    }

    private fun parseLayoutRoot(rootElement: Element): ParsedLayoutRoot {
        if (rootElement.tagName != "layout") {
            return ParsedLayoutRoot(
                node = parseElement(rootElement, 0),
                metadata = LayoutRootMetadata(originalRootTagName = rootElement.tagName)
            )
        }

        val elementChildren = rootElement.elementChildren()
        val dataElement = elementChildren
            .firstOrNull { it.element.tagName == "data" }
            ?.element
        val variables = dataElement?.dataBindingVariables().orEmpty()
        val imports = dataElement?.dataBindingImports().orEmpty()
        val viewRoot = elementChildren.singleOrNull { it.element.tagName != "data" }
        val isMalformed = viewRoot == null
        return ParsedLayoutRoot(
            node = if (viewRoot == null) parseElement(rootElement, 0) else parseElement(viewRoot.element, 0),
            metadata = LayoutRootMetadata(
                originalRootTagName = rootElement.tagName,
                isDataBindingLayout = true,
                isMalformedDataBindingLayout = isMalformed,
                dataBindingVariables = variables,
                dataBindingImports = imports
            )
        )
    }

    private fun parseElement(element: Element, indexInParent: Int): LayoutNode {
        val tagName = element.tagName
        val attributes = mutableMapOf<String, String>()

        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            attributes[attr.nodeName] = attr.nodeValue
        }

        val children = mutableListOf<LayoutNode>()
        var childIndex = 0
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                children.add(parseElement(child as Element, childIndex))
                childIndex++
            }
        }

        val nodeType = resolveNodeType(tagName, attributes)

        // For <include> with a resolver: attempt to resolve the included layout
        if (nodeType is LayoutNodeType.Include && includeResolver != null) {
            when (val resolved = includeResolver.resolveInclude(nodeType.layoutRef)) {
                is IncludeResult.Success -> {
                    val includedRoot = resolved.tree.root
                    val resolvedNodeType = nodeType.copy(
                        isDataBindingLayout = resolved.tree.rootMetadata.isDataBindingLayout
                    )

                    // If the included root is a <merge> tag, native Android ignores
                    // the layout_* and id attributes on the <include> tag.
                    if (includedRoot.tagName == "merge") {
                        return LayoutNode(
                            tagName = "merge",
                            attributes = includedRoot.attributes,
                            children = includedRoot.children,
                            indexInParent = indexInParent,
                            nodeType = resolvedNodeType
                        )
                    }

                    // Merge layout_* attrs from include tag onto the included root
                    val mergedAttrs = includedRoot.attributes.toMutableMap()
                    attributes.forEach { (key, value) ->
                        if (key.startsWith("android:layout_")) {
                            mergedAttrs[key] = value
                        }
                    }
                    // Carry over android:id from include tag if present
                    attributes["android:id"]?.let { mergedAttrs["android:id"] = it }
                    // Carry over android:visibility from include tag if present
                    attributes["android:visibility"]?.let { mergedAttrs["android:visibility"] = it }

                    return LayoutNode(
                        tagName = includedRoot.tagName,
                        attributes = mergedAttrs,
                        children = includedRoot.children,
                        indexInParent = indexInParent,
                        nodeType = resolvedNodeType
                    )
                }
                is IncludeResult.NotFound -> {
                    return LayoutNode(
                        tagName = tagName,
                        attributes = attributes,
                        children = children,
                        indexInParent = indexInParent,
                        nodeType = LayoutNodeType.Include(nodeType.layoutRef, "INCLUDE_NOT_FOUND")
                    )
                }
                is IncludeResult.CircularReference -> {
                    return LayoutNode(
                        tagName = tagName,
                        attributes = attributes,
                        children = children,
                        indexInParent = indexInParent,
                        nodeType = LayoutNodeType.Include(nodeType.layoutRef, "CIRCULAR_INCLUDE")
                    )
                }
                is IncludeResult.DepthExceeded -> {
                    return LayoutNode(
                        tagName = tagName,
                        attributes = attributes,
                        children = children,
                        indexInParent = indexInParent,
                        nodeType = LayoutNodeType.Include(nodeType.layoutRef, "INCLUDE_DEPTH_EXCEEDED")
                    )
                }
                is IncludeResult.ParseError -> {
                    return LayoutNode(
                        tagName = tagName,
                        attributes = attributes,
                        children = children,
                        indexInParent = indexInParent,
                        nodeType = LayoutNodeType.Include(nodeType.layoutRef, "INCLUDE_PARSE_ERROR")
                    )
                }
            }
        }

        return LayoutNode(
            tagName = tagName,
            attributes = attributes,
            children = children,
            indexInParent = indexInParent,
            nodeType = nodeType
        )
    }

    /**
     * Determines the [LayoutNodeType] based on tag name and attributes.
     */
    private fun resolveNodeType(tagName: String, attributes: Map<String, String>): LayoutNodeType {
        return when (tagName) {
            "include" -> {
                val layoutRef = extractLayoutRef(attributes["layout"])
                if (layoutRef != null) {
                    LayoutNodeType.Include(layoutRef, includeAttributes = attributes.toMap())
                } else {
                    LayoutNodeType.Regular
                }
            }
            "merge" -> LayoutNodeType.Merge
            "ViewStub" -> {
                val layoutRef = extractLayoutRef(attributes["android:layout"])
                if (layoutRef != null) LayoutNodeType.ViewStub(layoutRef) else LayoutNodeType.Regular
            }
            else -> LayoutNodeType.Regular
        }
    }

    /**
     * Extracts layout resource name from a `@layout/xxx` reference.
     * Returns null if the format is invalid.
     */
    private fun extractLayoutRef(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim()
        return if (trimmed.startsWith("@layout/")) trimmed.removePrefix("@layout/") else null
    }

    private fun Element.elementChildren(): List<IndexedElement> {
        val children = mutableListOf<IndexedElement>()
        var childIndex = 0
        val childNodes = childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                children.add(IndexedElement(childIndex, child as Element))
                childIndex++
            }
        }
        return children
    }

    private fun Element.dataBindingVariables(): List<DataBindingVariable> {
        return elementChildren()
            .asSequence()
            .map { it.element }
            .filter { it.tagName == "variable" }
            .mapNotNull { element ->
                val name = element.getAttribute("name").trim()
                val type = element.getAttribute("type").trim()
                if (name.isEmpty() || type.isEmpty()) {
                    null
                } else {
                    DataBindingVariable(name, type)
                }
            }
            .toList()
    }

    private fun Element.dataBindingImports(): List<DataBindingImport> {
        return elementChildren()
            .asSequence()
            .map { it.element }
            .filter { it.tagName == "import" }
            .mapNotNull { element ->
                val type = element.getAttribute("type").trim()
                val alias = element.getAttribute("alias").trim().takeIf { it.isNotEmpty() }
                if (type.isEmpty()) {
                    null
                } else {
                    DataBindingImport(type, alias)
                }
            }
            .toList()
    }

    private data class IndexedElement(
        val index: Int,
        val element: Element
    )

    private data class ParsedLayoutRoot(
        val node: LayoutNode,
        val metadata: LayoutRootMetadata
    )
}
